package com.example.bgrecorder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.PowerManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 화면 없이 카메라 영상을 녹화하는 포그라운드 서비스.
 * 녹화 중에는 상단 알림이 항상 표시된다 (Android 정책상 필수).
 */
class RecordingService : LifecycleService() {

    private var cameraProvider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var stopRequested = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }

        val useFront = intent?.getBooleanExtra(EXTRA_FRONT, false) ?: false
        val wantAudio = intent?.getBooleanExtra(EXTRA_AUDIO, true) ?: true
        val audio = wantAudio && hasPermission(Manifest.permission.RECORD_AUDIO)
        val qualityLevel = intent?.getIntExtra(EXTRA_QUALITY, Prefs.quality(this)) ?: Prefs.quality(this)
        val wide = intent?.getBooleanExtra(EXTRA_WIDE, false) ?: false

        // startForegroundService() 후에는 반드시 바로 startForeground()를 호출해야 한다.
        startAsForeground(audio)

        if (recording == null && !starting) {
            stopRequested = false
            acquireWakeLock()
            startRecording(useFront, audio, qualityLevel, wide)
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- 녹화

    @Volatile
    private var starting = false

    @SuppressLint("MissingPermission") // 위에서 hasPermission()으로 확인함
    private fun startRecording(useFront: Boolean, withAudio: Boolean, qualityLevel: Int, wide: Boolean) {
        starting = true
        isRecording.postValue(true)

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            starting = false
            if (stopRequested) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider

                // 고른 화질을 쓰고, 기기가 지원하지 않으면 그보다 낮은 화질 중 가장 높은 것으로 자동 대체
                val desired = when (qualityLevel) {
                    Prefs.QUALITY_HD -> Quality.HD
                    Prefs.QUALITY_UHD -> Quality.UHD
                    else -> Quality.FHD
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            desired,
                            FallbackStrategy.lowerQualityOrHigherThan(desired)
                        )
                    )
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                val selector = if (useFront) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    // 뒤쪽은 항상 이 필터를 거쳐서, '넓게'를 껐을 때도 기본 렌즈가
                    // 확실히 고정되도록 한다 (기기에 따라 기본 선택이 광각 렌즈가 되는 경우 방지).
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .addCameraFilter { infos ->
                            val picked = pickBackCamera(infos, wide)
                            if (picked != null) listOf(picked) else infos
                        }
                        .build()
                }

                // 미리보기(Preview) 없이 녹화만 바인딩한다.
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, videoCapture)

                val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/IMGIE")
                }
                val output = MediaStoreOutputOptions.Builder(
                    contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(values).build()

                var pending = recorder.prepareRecording(this, output)
                if (withAudio) pending = pending.withAudioEnabled()

                recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        // 정상 저장 시에는 팝업을 띄우지 않고, 오류일 때만 알린다.
                        if (event.hasError()) {
                            Toast.makeText(applicationContext, R.string.toast_error, Toast.LENGTH_LONG).show()
                        }
                        recording = null
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(applicationContext, R.string.toast_error, Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 후면 카메라 중 하나를 고른다.
     * wide=true  → 화각(FOV)이 가장 넓은 렌즈
     * wide=false → 기본 렌즈. 카메라ID "0"번이 있으면 그걸 쓰고(기기 대부분의 표준 규칙),
     *              없으면 화각이 가장 좁지 않은(=광각이 아닌) 렌즈 중 하나로 대체한다.
     * 정보를 못 읽으면 null (이 경우 호출 쪽에서 원래 목록을 그대로 쓴다).
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun pickBackCamera(infos: List<CameraInfo>, wide: Boolean): CameraInfo? {
        val manager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null

        data class Entry(val info: CameraInfo, val id: String, val fov: Double?)

        val entries = infos.map { info ->
            val id = try {
                Camera2CameraInfo.from(info).cameraId
            } catch (e: Exception) {
                null
            }
            val fov = id?.let { computeFov(manager, it) }
            Entry(info, id ?: "", fov)
        }

        if (wide) {
            return entries.maxByOrNull { it.fov ?: -1.0 }?.info
        }

        // 기본 렌즈: 안드로이드 관례상 후면 메인 렌즈는 카메라ID "0"
        entries.firstOrNull { it.id == "0" }?.let { return it.info }
        // ID "0"이 없으면 화각이 가장 좁은(=배율이 가장 높은, 보통 메인/망원) 렌즈로 대체
        return entries.minByOrNull { it.fov ?: Double.MAX_VALUE }?.info
    }

    private fun computeFov(manager: CameraManager, cameraId: String): Double? {
        return try {
            val chars = manager.getCameraCharacteristics(cameraId)
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val focal = focalLengths?.minOrNull()
            if (focal != null && focal > 0f && sensorSize != null) {
                2 * Math.toDegrees(Math.atan((sensorSize.width / (2 * focal)).toDouble()))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun stopRecording() {
        stopRequested = true
        val current = recording
        if (current != null) {
            // 파일 마무리가 끝나면 Finalize 이벤트에서 stopSelf()가 호출된다.
            current.stop()
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        recording?.stop()
        recording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        isRecording.postValue(false)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 알림 / 포그라운드

    private fun startAsForeground(audio: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        // 소리·진동·팝업이 없는 조용한 채널 (녹화 중 알림 자체는 Android 규칙상 항상 표시됨)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val stopIntent = Intent(this, RecordingService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPending = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.notif_stop), stopPending)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (audio) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BgRecorder::Recording").apply {
            acquire(4 * 60 * 60 * 1000L) // 안전장치: 최대 4시간
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------- 외부에서 쓰는 API

    companion object {
        const val ACTION_STOP = "com.example.bgrecorder.ACTION_STOP"
        const val EXTRA_FRONT = "extra_front"
        const val EXTRA_AUDIO = "extra_audio"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_WIDE = "extra_wide"

        private const val CHANNEL_ID = "status"
        private const val NOTIF_ID = 1001

        /** 화면(Activity)이 진행 상태를 관찰하는 용도 */
        val isRecording = MutableLiveData(false)

        fun start(context: Context, front: Boolean, audio: Boolean, quality: Int, wide: Boolean) {
            val intent = Intent(context, RecordingService::class.java)
                .putExtra(EXTRA_FRONT, front)
                .putExtra(EXTRA_AUDIO, audio)
                .putExtra(EXTRA_QUALITY, quality)
                .putExtra(EXTRA_WIDE, wide)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

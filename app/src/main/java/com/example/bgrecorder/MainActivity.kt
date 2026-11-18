package com.example.bgrecorder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var audioSwitch: MaterialSwitch
    private lateinit var wideSwitch: MaterialSwitch
    private lateinit var cameraGroup: RadioGroup
    private lateinit var radioFront: RadioButton
    private lateinit var qualityGroup: RadioGroup
    private lateinit var iconGroup: RadioGroup

    private val iconButtonIds = IntArray(5)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (isGranted(Manifest.permission.CAMERA)) {
                startRecording()
            } else {
                Toast.makeText(this, R.string.toast_permission, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        audioSwitch = findViewById(R.id.audioSwitch)
        wideSwitch = findViewById(R.id.wideSwitch)
        cameraGroup = findViewById(R.id.cameraGroup)
        radioFront = findViewById(R.id.radioFront)
        qualityGroup = findViewById(R.id.qualityGroup)
        iconGroup = findViewById(R.id.iconGroup)

        iconButtonIds[0] = R.id.iconBattery
        iconButtonIds[1] = R.id.iconCalc
        iconButtonIds[2] = R.id.iconMemo
        iconButtonIds[3] = R.id.iconWeather
        iconButtonIds[4] = R.id.iconCalendar

        // 지난번에 고른 설정 복원
        if (Prefs.useFront(this)) radioFront.isChecked = true else findViewById<RadioButton>(R.id.radioBack).isChecked = true
        audioSwitch.isChecked = Prefs.useAudio(this)
        wideSwitch.isChecked = Prefs.useWide(this)
        qualityGroup.check(
            when (Prefs.quality(this)) {
                Prefs.QUALITY_HD -> R.id.radioHd
                Prefs.QUALITY_UHD -> R.id.radioUhd
                else -> R.id.radioFhd
            }
        )
        iconGroup.check(iconButtonIds[Prefs.iconIndex(this)])

        // 앱을 열 때마다, 길게 누르기 메뉴의 바로가기 아이콘도 현재 고른 아이콘과 맞춘다.
        ShortcutsHelper.refreshDynamic(this)

        startButton.setOnClickListener { requestPermissionsThenStart() }
        stopButton.setOnClickListener { RecordingService.stop(this) }
        findViewById<Button>(R.id.pinButton).setOnClickListener { pinShortcut() }

        findViewById<Button>(R.id.batteryButton).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        // 설정을 바꾸는 즉시 저장한다 (시작 버튼을 누르지 않아도 바로가기에 바로 반영되도록).
        cameraGroup.setOnCheckedChangeListener { _, _ -> persistCurrentSettings() }
        wideSwitch.setOnCheckedChangeListener { _, _ -> persistCurrentSettings() }
        audioSwitch.setOnCheckedChangeListener { _, _ -> persistCurrentSettings() }
        qualityGroup.setOnCheckedChangeListener { _, _ -> persistCurrentSettings() }

        iconGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = iconButtonIds.indexOf(checkedId)
            if (index >= 0) {
                Prefs.saveIconIndex(this, index)
                IconManager.apply(this, index)
                ShortcutsHelper.refreshDynamic(this)
                Toast.makeText(this, R.string.toast_icon_changed, Toast.LENGTH_SHORT).show()
            }
        }

        RecordingService.isRecording.observe(this) { recording -> render(recording == true) }
    }

    /** 현재 화면의 설정을 바로 저장한다. 바로가기는 이 값을 그대로 읽어서 쓴다. */
    private fun persistCurrentSettings() {
        Prefs.save(this, radioFront.isChecked, audioSwitch.isChecked, currentQuality(), wideSwitch.isChecked)
    }

    private fun render(recording: Boolean) {
        statusText.setText(if (recording) R.string.status_recording else R.string.status_idle)
        startButton.isEnabled = !recording
        stopButton.isEnabled = recording
        audioSwitch.isEnabled = !recording
        wideSwitch.isEnabled = !recording
        for (i in 0 until cameraGroup.childCount) cameraGroup.getChildAt(i).isEnabled = !recording
        for (i in 0 until qualityGroup.childCount) qualityGroup.getChildAt(i).isEnabled = !recording
    }

    private fun currentQuality(): Int = when (qualityGroup.checkedRadioButtonId) {
        R.id.radioHd -> Prefs.QUALITY_HD
        R.id.radioUhd -> Prefs.QUALITY_UHD
        else -> Prefs.QUALITY_FHD
    }

    private fun requestPermissionsThenStart() {
        val missing = mutableListOf<String>()
        if (!isGranted(Manifest.permission.CAMERA)) missing += Manifest.permission.CAMERA
        if (audioSwitch.isChecked && !isGranted(Manifest.permission.RECORD_AUDIO)) {
            missing += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }

        if (missing.isEmpty()) startRecording() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startRecording() {
        // 바로가기에서도 같은 설정을 쓰도록 저장
        persistCurrentSettings()
        // 반드시 앱 화면이 보이는 상태에서 시작해야 Android 12+ 제한에 걸리지 않는다.
        RecordingService.start(this, radioFront.isChecked, audioSwitch.isChecked, currentQuality(), wideSwitch.isChecked)
    }

    /** 홈 화면에 빠른 시작 아이콘을 만든다 (누르면 즉시 시작/중지). 현재 고른 아이콘을 그대로 쓴다. */
    private fun pinShortcut() {
        // 설정을 바꾸고 바로 눌렀을 때도 반영되도록 먼저 저장
        persistCurrentSettings()

        val manager = getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            Toast.makeText(this, R.string.toast_pin_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, QuickRecordActivity::class.java)
            .setAction(QuickRecordActivity.ACTION_TOGGLE)
        val info = ShortcutInfo.Builder(this, "quick_record_pinned")
            .setShortLabel(getString(R.string.shortcut_short))
            .setLongLabel(getString(R.string.shortcut_long))
            .setIcon(Icon.createWithResource(this, ShortcutsHelper.pinnedIconRes(this)))
            .setIntent(intent)
            .build()
        manager.requestPinShortcut(info, null)
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

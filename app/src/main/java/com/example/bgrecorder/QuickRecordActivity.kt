package com.example.bgrecorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 바로가기(길게 누르기 메뉴 / 홈 화면 아이콘)로 열리는 투명 화면.
 * 화면 없이 녹화를 시작하거나(이미 녹화 중이면) 중지하고 곧바로 닫힌다.
 *
 * 앱 화면이 잠깐이라도 '보이는 상태'에서 서비스를 시작하므로
 * Android 12+ 의 백그라운드 카메라 시작 제한에 걸리지 않는다.
 */
class QuickRecordActivity : AppCompatActivity() {

    private var handled = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (isGranted(Manifest.permission.CAMERA)) {
                startRecording()
            } else {
                Toast.makeText(applicationContext, R.string.toast_permission, Toast.LENGTH_LONG).show()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면 없음 (투명 테마). 실제 동작은 onResume 에서 한 번만 실행한다.
    }

    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true

        if (RecordingService.isRecording.value == true) {
            RecordingService.stop(this)
            Toast.makeText(applicationContext, R.string.toast_stopping, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val audio = Prefs.useAudio(this)
        val missing = mutableListOf<String>()
        if (!isGranted(Manifest.permission.CAMERA)) missing += Manifest.permission.CAMERA
        if (audio && !isGranted(Manifest.permission.RECORD_AUDIO)) {
            missing += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }

        if (missing.isEmpty()) {
            startRecording()
            finish()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startRecording() {
        RecordingService.start(this, Prefs.useFront(this), Prefs.useAudio(this))
        Toast.makeText(applicationContext, R.string.toast_started, Toast.LENGTH_SHORT).show()
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_TOGGLE = "com.example.bgrecorder.QUICK_TOGGLE"
    }
}

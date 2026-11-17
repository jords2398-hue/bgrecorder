package com.example.bgrecorder

import android.content.Context

/** 마지막으로 고른 카메라/화질/소리 설정을 저장해서, 바로가기에서도 같은 설정으로 녹화한다. */
object Prefs {
    private const val NAME = "bgrecorder"
    private const val KEY_FRONT = "front"
    private const val KEY_AUDIO = "audio"
    private const val KEY_QUALITY = "quality"

    /** 화질 단계: 0 = HD(720p), 1 = FHD(1080p), 2 = UHD(4K) */
    const val QUALITY_HD = 0
    const val QUALITY_FHD = 1
    const val QUALITY_UHD = 2

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun useFront(context: Context) = sp(context).getBoolean(KEY_FRONT, false)

    fun useAudio(context: Context) = sp(context).getBoolean(KEY_AUDIO, true)

    /** 기본값은 1080p */
    fun quality(context: Context) = sp(context).getInt(KEY_QUALITY, QUALITY_FHD)

    fun save(context: Context, front: Boolean, audio: Boolean, quality: Int) {
        sp(context).edit()
            .putBoolean(KEY_FRONT, front)
            .putBoolean(KEY_AUDIO, audio)
            .putInt(KEY_QUALITY, quality)
            .apply()
    }
}

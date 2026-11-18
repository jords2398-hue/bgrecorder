package com.example.bgrecorder

import android.content.Context

/** 마지막으로 고른 설정을 저장해서, 바로가기에서도 같은 설정으로 시작한다. */
object Prefs {
    private const val NAME = "bgrecorder"
    private const val KEY_FRONT = "front"
    private const val KEY_AUDIO = "audio"
    private const val KEY_QUALITY = "quality"
    private const val KEY_WIDE = "wide"
    private const val KEY_ICON = "icon"

    /** 단계: 0 = 저용량(720p), 1 = 보통(1080p), 2 = 고용량(4K) */
    const val QUALITY_HD = 0
    const val QUALITY_FHD = 1
    const val QUALITY_UHD = 2

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun useFront(context: Context) = sp(context).getBoolean(KEY_FRONT, false)

    fun useAudio(context: Context) = sp(context).getBoolean(KEY_AUDIO, true)

    /** 기본값은 1080p */
    fun quality(context: Context) = sp(context).getInt(KEY_QUALITY, QUALITY_FHD)

    /** 뒤쪽을 고른 경우에만 적용되는 넓은 화각 옵션 */
    fun useWide(context: Context) = sp(context).getBoolean(KEY_WIDE, false)

    fun save(context: Context, front: Boolean, audio: Boolean, quality: Int, wide: Boolean) {
        sp(context).edit()
            .putBoolean(KEY_FRONT, front)
            .putBoolean(KEY_AUDIO, audio)
            .putInt(KEY_QUALITY, quality)
            .putBoolean(KEY_WIDE, wide)
            .apply()
    }

    /** 홈 화면 아이콘 선택지 (0~4번, IconManager.ALIASES 인덱스와 맞춤) */
    fun iconIndex(context: Context) = sp(context).getInt(KEY_ICON, 0)

    fun saveIconIndex(context: Context, index: Int) {
        sp(context).edit().putInt(KEY_ICON, index).apply()
    }
}

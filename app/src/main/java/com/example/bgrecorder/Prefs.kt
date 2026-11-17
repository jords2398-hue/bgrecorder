package com.example.bgrecorder

import android.content.Context

/** 마지막으로 고른 카메라/소리 설정을 저장해서, 바로가기에서도 같은 설정으로 녹화한다. */
object Prefs {
    private const val NAME = "bgrecorder"
    private const val KEY_FRONT = "front"
    private const val KEY_AUDIO = "audio"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun useFront(context: Context) = sp(context).getBoolean(KEY_FRONT, false)

    fun useAudio(context: Context) = sp(context).getBoolean(KEY_AUDIO, true)

    fun save(context: Context, front: Boolean, audio: Boolean) {
        sp(context).edit().putBoolean(KEY_FRONT, front).putBoolean(KEY_AUDIO, audio).apply()
    }
}

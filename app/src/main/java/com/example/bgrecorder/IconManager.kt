package com.example.bgrecorder

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** 홈 화면 아이콘을 전환한다 (activity-alias 5종 중 하나만 켠다). */
object IconManager {

    private const val PREFIX = "com.example.bgrecorder."
    val ALIASES = listOf(
        PREFIX + "IconBattery",
        PREFIX + "IconCalc",
        PREFIX + "IconMemo",
        PREFIX + "IconWeather",
        PREFIX + "IconCalendar"
    )

    fun apply(context: Context, index: Int) {
        val pm = context.packageManager
        val target = index.coerceIn(0, ALIASES.lastIndex)
        ALIASES.forEachIndexed { i, name ->
            val state = if (i == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, name),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}

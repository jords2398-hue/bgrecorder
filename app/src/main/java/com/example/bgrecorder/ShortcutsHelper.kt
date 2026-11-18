package com.example.bgrecorder

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/**
 * 길게 누르기 메뉴와 홈 화면 고정 바로가기의 아이콘을,
 * 지금 고른 앱 아이콘(배터리/계산기/메모/날씨/달력)에 맞춰준다.
 */
object ShortcutsHelper {
    private const val DYNAMIC_ID = "quick_record"

    /** 앱 아이콘 길게 누르기 메뉴. 아이콘을 바꿀 때마다, 그리고 앱을 열 때마다 다시 맞춘다. */
    fun refreshDynamic(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        try {
            val iconRes = IconManager.drawableRes(Prefs.iconIndex(context))
            val intent = Intent(context, QuickRecordActivity::class.java)
                .setAction(QuickRecordActivity.ACTION_TOGGLE)
            val info = ShortcutInfo.Builder(context, DYNAMIC_ID)
                .setShortLabel(context.getString(R.string.shortcut_short))
                .setLongLabel(context.getString(R.string.shortcut_long))
                .setIcon(Icon.createWithResource(context, iconRes))
                .setIntent(intent)
                .build()
            manager.dynamicShortcuts = listOf(info)
        } catch (e: Exception) {
            // 일부 런처/기기에서 실패해도 앱 동작에는 영향 없음
        }
    }

    /** 홈 화면에 직접 고정하는 바로가기도 같은 아이콘을 쓴다. */
    fun pinnedIconRes(context: Context): Int = IconManager.drawableRes(Prefs.iconIndex(context))
}

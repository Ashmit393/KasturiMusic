package com.ashmit.kasturimusic

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window

/** Library / Settings screen ke colors. Now Playing hamesha dark glass rehta hai (Ui.* colors). */
object Theme {
    const val LIGHT = 0
    const val DARK = 1
    const val AMOLED = 2

    private val mode: Int
        get() = SettingsManager.theme

    val isLight: Boolean
        get() = mode == LIGHT

    val bg: Int
        get() = when (mode) {
            LIGHT -> Color.parseColor("#FFFFFF")
            DARK -> Color.parseColor("#121214")
            else -> Color.BLACK
        }

    val card: Int
        get() = when (mode) {
            LIGHT -> Color.parseColor("#F2F2F7")
            DARK -> Color.parseColor("#1F1F23")
            else -> Color.parseColor("#1C1C1E")
        }

    val text: Int
        get() = if (isLight) Color.BLACK else Color.WHITE

    val sub: Int
        get() = if (isLight) Color.parseColor("#6C6C70") else Color.parseColor("#8E8E93")

    val accent: Int
        get() = if (isLight) Color.parseColor("#3A3A3C") else Color.parseColor("#C0C0C0")

    val onAccent: Int
        get() = if (isLight) Color.WHITE else Color.BLACK

    val dialogStyle: Int
        get() = if (isLight) android.R.style.Theme_Material_Light_Dialog_Alert
        else android.R.style.Theme_Material_Dialog_Alert

    /** Status bar, navigation bar aur window background theme ke hisaab se. */
    fun applyBars(w: Window) {
        w.statusBarColor = bg
        w.navigationBarColor = bg
        w.setBackgroundDrawable(ColorDrawable(bg))
        w.decorView.systemUiVisibility =
            if (isLight) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
    }
}

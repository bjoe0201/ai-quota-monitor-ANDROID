package com.example.ai_quota_monitor_android.util

import androidx.compose.ui.graphics.Color

/**
 * Blend two colors. alpha=0 -> bg, alpha=1 -> fg.
 * Ported from gui/widgets.py _mix_hex().
 */
fun mixColor(fg: Color, bg: Color, alpha: Float): Color {
    return Color(
        red = fg.red * alpha + bg.red * (1 - alpha),
        green = fg.green * alpha + bg.green * (1 - alpha),
        blue = fg.blue * alpha + bg.blue * (1 - alpha),
        alpha = 1f,
    )
}

fun headerTint(accent: Color, baseBg: Color = Color(0xFF111114)): Color {
    return mixColor(accent, baseBg, 0.18f)
}

fun percentColor(pct: Float): Color = when {
    pct >= 85f -> Color(0xFFF87171) // error
    pct >= 60f -> Color(0xFFFBBF24) // warning
    else -> Color(0xFF60A5FA)       // info
}

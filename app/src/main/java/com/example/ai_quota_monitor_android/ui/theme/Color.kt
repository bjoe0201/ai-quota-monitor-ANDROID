package com.example.ai_quota_monitor_android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Ported from gui/widgets.py COLORS dict (Linear / Raycast dark theme).
 */
object AppColors {
    // Backgrounds
    val Bg = Color(0xFF0A0A0C)
    val CardBg = Color(0xFF111114)
    val CardBgHover = Color(0xFF16161A)
    val TitleBg = Color(0xFF0A0A0C)

    // Borders
    val Border = Color(0xFF1F1F24)
    val BorderStrong = Color(0xFF2A2A32)
    val CardBorder = Color(0xFF1F1F24)

    // Text
    val Text = Color(0xFFFAFAFA)
    val TextMuted = Color(0xFFA1A1AA)
    val TextDim = Color(0xFF71717A)
    val TextFaint = Color(0xFF52525B)
    val Subtext = Color(0xFF71717A)

    // Semantic
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
    val Info = Color(0xFF60A5FA)
    val Violet = Color(0xFFA78BFA)
    val Peach = Color(0xFFFBBF24)
    val Green = Color(0xFF34D399)
    val Accent = Color(0xFF60A5FA)
}

/** Per-service accent colors for header tinting. */
object ServiceAccents {
    val map = mapOf(
        "browser_openai" to Color(0xFF60A5FA),
        "browser_claude_usage" to Color(0xFFA78BFA),
        "browser_claude_billing" to Color(0xFFC084FC),
        "browser_github_copilot" to Color(0xFF34D399),
        "browser_openrouter" to Color(0xFF818CF8),
    )

    fun get(serviceKey: String): Color = map[serviceKey] ?: AppColors.Info
}

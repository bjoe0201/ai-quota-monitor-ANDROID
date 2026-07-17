package com.example.ai_quota_monitor_android.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * All theme colors in one data class so CompositionLocal can switch between dark/light.
 */
data class AppColorSet(
    val Bg: Color,
    val CardBg: Color,
    val CardBgHover: Color,
    val TitleBg: Color,
    val Border: Color,
    val BorderStrong: Color,
    val CardBorder: Color,
    val Text: Color,
    val TextMuted: Color,
    val TextDim: Color,
    val TextFaint: Color,
    val Subtext: Color,
    val Success: Color,
    val Warning: Color,
    val Error: Color,
    val Info: Color,
    val Violet: Color,
    val Peach: Color,
    val Green: Color,
    val Accent: Color,
)

/** Ported from gui/widgets.py COLORS dict (Linear / Raycast dark theme). */
val DarkAppColors = AppColorSet(
    Bg = Color(0xFF0A0A0C),
    CardBg = Color(0xFF111114),
    CardBgHover = Color(0xFF16161A),
    TitleBg = Color(0xFF0A0A0C),
    Border = Color(0xFF1F1F24),
    BorderStrong = Color(0xFF2A2A32),
    CardBorder = Color(0xFF1F1F24),
    Text = Color(0xFFFAFAFA),
    TextMuted = Color(0xFFA1A1AA),
    TextDim = Color(0xFF71717A),
    TextFaint = Color(0xFF52525B),
    Subtext = Color(0xFF71717A),
    Success = Color(0xFF34D399),
    Warning = Color(0xFFFBBF24),
    Error = Color(0xFFF87171),
    Info = Color(0xFF60A5FA),
    Violet = Color(0xFFA78BFA),
    Peach = Color(0xFFFBBF24),
    Green = Color(0xFF34D399),
    Accent = Color(0xFF60A5FA),
)

/** Clean neutral light theme. */
val LightAppColors = AppColorSet(
    Bg = Color(0xFFF4F4F1),
    CardBg = Color(0xFFFFFFFF),
    CardBgHover = Color(0xFFEEEEEB),
    TitleBg = Color(0xFFF4F4F1),
    Border = Color(0xFFE4E4E7),
    BorderStrong = Color(0xFFC8C8CC),
    CardBorder = Color(0xFFE4E4E7),
    Text = Color(0xFF0F0F12),
    TextMuted = Color(0xFF52525B),
    TextDim = Color(0xFF71717A),
    TextFaint = Color(0xFFA1A1AA),
    Subtext = Color(0xFF71717A),
    Success = Color(0xFF16A34A),
    Warning = Color(0xFFD97706),
    Error = Color(0xFFDC2626),
    Info = Color(0xFF2563EB),
    Violet = Color(0xFF7C3AED),
    Peach = Color(0xFFD97706),
    Green = Color(0xFF16A34A),
    Accent = Color(0xFF2563EB),
)

val LocalAppColors = compositionLocalOf { DarkAppColors }

/** Per-service accent colors for header tinting. */
object ServiceAccents {
    val map = mapOf(
        "browser_openai" to Color(0xFF60A5FA),
        "browser_claude_usage" to Color(0xFFA78BFA),
        "browser_claude_billing" to Color(0xFFC084FC),
        "browser_github_copilot" to Color(0xFF34D399),
        "browser_openrouter" to Color(0xFF818CF8),
        "browser_chatgpt_usage" to Color(0xFF10A37F),
    )

    fun get(serviceKey: String): Color = map[serviceKey] ?: Color(0xFF60A5FA)
}

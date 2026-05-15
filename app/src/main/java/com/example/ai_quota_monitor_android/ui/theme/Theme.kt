package com.example.ai_quota_monitor_android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ai_quota_monitor_android.data.model.ThemeMode

private val MaterialDarkScheme = darkColorScheme(
    primary = DarkAppColors.Accent,
    secondary = DarkAppColors.Violet,
    tertiary = DarkAppColors.Green,
    background = DarkAppColors.Bg,
    surface = DarkAppColors.CardBg,
    onPrimary = DarkAppColors.Bg,
    onSecondary = DarkAppColors.Bg,
    onTertiary = DarkAppColors.Bg,
    onBackground = DarkAppColors.Text,
    onSurface = DarkAppColors.Text,
    error = DarkAppColors.Error,
    onError = DarkAppColors.Bg,
    surfaceVariant = DarkAppColors.CardBorder,
    onSurfaceVariant = DarkAppColors.TextMuted,
    outline = DarkAppColors.Border,
)

private val MaterialLightScheme = lightColorScheme(
    primary = LightAppColors.Accent,
    secondary = LightAppColors.Violet,
    tertiary = LightAppColors.Green,
    background = LightAppColors.Bg,
    surface = LightAppColors.CardBg,
    onPrimary = LightAppColors.CardBg,
    onSecondary = LightAppColors.CardBg,
    onTertiary = LightAppColors.CardBg,
    onBackground = LightAppColors.Text,
    onSurface = LightAppColors.Text,
    error = LightAppColors.Error,
    onError = LightAppColors.CardBg,
    surfaceVariant = LightAppColors.CardBorder,
    onSurfaceVariant = LightAppColors.TextMuted,
    outline = LightAppColors.Border,
)

@Composable
fun AiQuotaMonitorTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    val colors = if (themeMode == ThemeMode.Dark) DarkAppColors else LightAppColors
    val colorScheme = if (themeMode == ThemeMode.Dark) MaterialDarkScheme else MaterialLightScheme
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

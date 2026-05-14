package com.example.ai_quota_monitor_android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Accent,
    secondary = AppColors.Violet,
    tertiary = AppColors.Green,
    background = AppColors.Bg,
    surface = AppColors.CardBg,
    onPrimary = AppColors.Bg,
    onSecondary = AppColors.Bg,
    onTertiary = AppColors.Bg,
    onBackground = AppColors.Text,
    onSurface = AppColors.Text,
    error = AppColors.Error,
    onError = AppColors.Bg,
    surfaceVariant = AppColors.CardBorder,
    onSurfaceVariant = AppColors.TextMuted,
    outline = AppColors.Border,
)

@Composable
fun AiQuotaMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}

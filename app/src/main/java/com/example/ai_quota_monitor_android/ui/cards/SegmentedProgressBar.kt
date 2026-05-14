package com.example.ai_quota_monitor_android.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ai_quota_monitor_android.ui.theme.AppColors

/**
 * 28-segment progress bar, ported from gui/widgets.py ProgressBar.
 */
@Composable
fun SegmentedProgressBar(
    percent: Float,
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 28,
    height: Int = 6,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
    ) {
        val gap = 2.dp.toPx()
        val totalGap = gap * (segments - 1)
        val segW = (size.width - totalGap) / segments
        val filled = ((percent / 100f) * segments).toInt().coerceIn(0, segments)
        val radius = CornerRadius(2f, 2f)

        for (i in 0 until segments) {
            val x = i * (segW + gap)
            val c = if (i < filled) color else AppColors.Border
            drawRoundRect(
                color = c,
                topLeft = Offset(x, 0f),
                size = Size(segW, size.height),
                cornerRadius = radius,
            )
        }
    }
}

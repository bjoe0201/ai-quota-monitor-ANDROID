package com.example.ai_quota_monitor_android.ui.cards

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.ui.theme.AppColors
import com.example.ai_quota_monitor_android.util.percentColor

@Composable
fun BarRow(
    label: String,
    percent: Float,
    detail: String = "",
    color: Color? = null,
    resetText: String? = null,
    resetUrgent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val barColor = color ?: percentColor(percent)

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Top: label + reset pill + percent
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, color = AppColors.TextDim, fontSize = 9.sp)
                if (resetText != null) {
                    val pillColor = if (resetUrgent) AppColors.Warning else AppColors.Violet
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .border(1.dp, pillColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "\u21bb $resetText",
                            color = pillColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Text(
                text = "%.1f%%".format(percent),
                color = barColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Progress bar
        SegmentedProgressBar(
            percent = percent,
            color = barColor,
            modifier = Modifier.padding(top = 2.dp),
        )

        // Detail
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                color = AppColors.TextFaint,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

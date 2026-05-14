package com.example.ai_quota_monitor_android.ui.cards

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.ui.theme.AppColors

@Composable
fun HeroSection(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = AppColors.Text,
    valueUnit: String? = null,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // Label row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                color = AppColors.TextDim,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .border(1.dp, AppColors.Border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = badge, color = AppColors.TextMuted, fontSize = 8.sp)
                }
            }
        }
        // Value row
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            if (valueUnit != null) {
                Text(
                    text = valueUnit,
                    color = AppColors.TextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                )
            }
        }
        HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(top = 6.dp))
    }
}

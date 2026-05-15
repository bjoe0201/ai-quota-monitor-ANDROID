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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.ui.theme.LocalAppColors

@Composable
fun HeroSection(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    valueUnit: String? = null,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                color = colors.TextDim,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .border(1.dp, colors.Border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = badge, color = colors.TextMuted, fontSize = 8.sp)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = value,
                color = if (valueColor == Color.Unspecified) colors.Text else valueColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            if (valueUnit != null) {
                Text(
                    text = valueUnit,
                    color = colors.TextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                )
            }
        }
        HorizontalDivider(color = colors.Border, modifier = Modifier.padding(top = 6.dp))
    }
}

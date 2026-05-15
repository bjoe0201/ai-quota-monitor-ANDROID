package com.example.ai_quota_monitor_android.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.ui.theme.LocalAppColors

@Composable
fun KvRow(
    label: String,
    value: String = "",
    valueColor: Color = Color.Unspecified,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = colors.TextDim, fontSize = 9.sp)
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    color = if (valueColor == Color.Unspecified) colors.Text else valueColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = colors.Border)
        }
    }
}

@Composable
fun KvPairRow(
    leftLabel: String,
    leftValue: String = "",
    rightLabel: String,
    rightValue: String = "",
    showDivider: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        ) {
            Row(modifier = Modifier.weight(1f)) {
                Text(text = leftLabel, color = colors.TextDim, fontSize = 9.sp)
                Text(
                    text = leftValue,
                    color = colors.Text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                Text(text = rightLabel, color = colors.TextDim, fontSize = 9.sp)
                Text(
                    text = rightValue,
                    color = colors.Text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = colors.Border)
        }
    }
}

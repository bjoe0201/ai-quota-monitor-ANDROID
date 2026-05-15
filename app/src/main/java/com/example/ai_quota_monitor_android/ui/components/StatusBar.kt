package com.example.ai_quota_monitor_android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.ui.theme.LocalAppColors

@Composable
fun StatusBar(
    serverRunning: Boolean,
    connectedServices: Int,
    totalServices: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.Bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\u25CF",
                color = if (serverRunning) colors.Success else colors.Error,
                fontSize = 6.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = if (serverRunning) "HTTP :7890" else "伺服器離線",
                color = colors.TextDim,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "$connectedServices / $totalServices 已連線",
            color = colors.TextDim,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

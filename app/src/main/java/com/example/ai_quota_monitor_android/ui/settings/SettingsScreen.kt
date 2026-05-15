package com.example.ai_quota_monitor_android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.ai_quota_monitor_android.data.model.DashboardConfig
import com.example.ai_quota_monitor_android.data.model.DashboardLayout
import com.example.ai_quota_monitor_android.ui.dashboard.DashboardViewModel
import com.example.ai_quota_monitor_android.ui.theme.AppColors
import com.example.ai_quota_monitor_android.ui.theme.ServiceAccents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onLoginService: (serviceKey: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val config = state.config

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Bg,
                    titleContentColor = AppColors.Text,
                    navigationIconContentColor = AppColors.TextMuted,
                ),
            )
        },
        containerColor = AppColors.Bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // -- Dashboard layout picker --
            SectionTitle("Dashboard 佈局")
            SettingsCard {
                val layouts = listOf(
                    Triple(DashboardLayout.A, "時鐘+服務並排", "時鐘縮小靠左，兩張卡並排"),
                    Triple(DashboardLayout.B, "左側 Sidebar", "橫式側欄，直式上方 Banner"),
                    Triple(DashboardLayout.C, "頂部橫條時鐘", "時鐘塞進頂欄，最大化卡片空間"),
                    Triple(DashboardLayout.D, "Bento Mosaic", "不對稱馬賽克，最有儀表板感"),
                )
                layouts.forEachIndexed { i, (id, label, desc) ->
                    if (i > 0) HorizontalDivider(color = AppColors.Border)
                    LayoutPickerRow(
                        id = id,
                        label = label,
                        desc = desc,
                        selected = config.dashboardLayout == id,
                        onSelect = { viewModel.updateConfig(config.copy(dashboardLayout = id)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -- Server settings --
            SectionTitle("HTTP 伺服器")
            SettingsCard {
                ToggleRow(
                    label = "啟用本機伺服器",
                    sublabel = "Port ${config.serverPort}，接收 Tampermonkey 資料",
                    checked = config.serverEnabled,
                    onCheckedChange = {
                        viewModel.updateConfig(config.copy(serverEnabled = it))
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -- Service accounts --
            SectionTitle("服務帳號")
            SettingsCard {
                config.services.entries.forEachIndexed { i, (key, svc) ->
                    if (i > 0) HorizontalDivider(color = AppColors.Border)
                    val authStatus = config.authStatus[key]
                    val isLoggedIn = authStatus?.loggedIn == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoginService(key) }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = svc.displayName,
                                color = AppColors.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isLoggedIn) "已登入" else "尚未登入",
                                color = if (isLoggedIn) AppColors.Success else AppColors.TextDim,
                                fontSize = 9.sp,
                            )
                        }
                        Text(
                            text = "登入 \u276F",
                            color = ServiceAccents.get(key),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -- About --
            SectionTitle("關於")
            SettingsCard {
                KvSettingsRow("版本", "1.2")
                HorizontalDivider(color = AppColors.Border)
                KvSettingsRow("資料來源", "WebView + HTTP Server")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = AppColors.TextDim,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.CardBg)
            .padding(horizontal = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    label: String,
    sublabel: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = AppColors.Text, fontSize = 12.sp)
            if (sublabel.isNotEmpty()) {
                Text(text = sublabel, color = AppColors.TextDim, fontSize = 9.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.Success,
                uncheckedTrackColor = AppColors.Border,
            ),
        )
    }
}

@Composable
private fun KvSettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = AppColors.TextDim, fontSize = 11.sp)
        Text(text = value, color = AppColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LayoutPickerRow(
    id: DashboardLayout,
    label: String,
    desc: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(if (selected) AppColors.CardBgHover else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LayoutMiniPreview(id)
        Column(Modifier.weight(1f)) {
            Text(text = label, color = AppColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = desc, color = AppColors.TextDim, fontSize = 9.sp)
        }
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) AppColors.Accent else Color.Transparent)
                .border(1.5.dp, if (selected) AppColors.Accent else AppColors.TextDim, CircleShape),
        )
    }
}

@Composable
private fun LayoutMiniPreview(variant: DashboardLayout) {
    val clockColor = AppColors.Accent.copy(alpha = 0.65f)
    val cardColor = AppColors.BorderStrong

    Box(
        modifier = Modifier
            .width(42.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AppColors.Bg),
    ) {
        when (variant) {
            DashboardLayout.A -> Column(
                Modifier.fillMaxSize().padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(0.8f).fillMaxHeight().background(clockColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                }
            }
            DashboardLayout.B -> Row(
                Modifier.fillMaxSize().padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.width(9.dp).fillMaxHeight().background(clockColor).clip(RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                        Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                        Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    }
                }
            }
            DashboardLayout.C -> Column(
                Modifier.fillMaxSize().padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(clockColor).clip(RoundedCornerShape(2.dp)))
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                }
            }
            DashboardLayout.D -> Column(
                Modifier.fillMaxSize().padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.weight(1.2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(0.7f).fillMaxHeight().background(clockColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1.4f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardColor).clip(RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.data.model.DashboardLayout
import com.example.ai_quota_monitor_android.data.model.ThemeMode
import com.example.ai_quota_monitor_android.data.model.effectiveServiceOrder
import com.example.ai_quota_monitor_android.ui.dashboard.DashboardViewModel
import com.example.ai_quota_monitor_android.ui.theme.LocalAppColors
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
    val colors = LocalAppColors.current

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
                    containerColor = colors.Bg,
                    titleContentColor = colors.Text,
                    navigationIconContentColor = colors.TextMuted,
                ),
            )
        },
        containerColor = colors.Bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── Theme ──────────────────────────────────────────────────────
            SectionTitle("主題")
            SettingsCard {
                ToggleRow(
                    label = "淺色模式",
                    sublabel = if (config.themeMode == ThemeMode.Light) "Light — 白底淺色" else "Dark — 暗色（預設）",
                    checked = config.themeMode == ThemeMode.Light,
                    onCheckedChange = {
                        viewModel.updateConfig(config.copy(themeMode = if (it) ThemeMode.Light else ThemeMode.Dark))
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Dashboard layout picker ────────────────────────────────────
            SectionTitle("Dashboard 佈局")
            SettingsCard {
                val layouts = listOf(
                    Triple(DashboardLayout.A, "時鐘+服務並排", "時鐘縮小靠左，兩張卡並排"),
                    Triple(DashboardLayout.B, "左側 Sidebar", "橫式側欄，直式上方 Banner"),
                    Triple(DashboardLayout.C, "頂部橫條時鐘", "時鐘塞進頂欄，最大化卡片空間"),
                    Triple(DashboardLayout.D, "Bento Mosaic", "不對稱馬賽克，最有儀表板感"),
                )
                layouts.forEachIndexed { i, (id, label, desc) ->
                    if (i > 0) HorizontalDivider(color = colors.Border)
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

            // ── Auto-refresh interval ──────────────────────────────────────
            SectionTitle("自動更新設定")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "WebView 更新間隔", color = colors.Text, fontSize = 12.sp)
                        Text(text = "每隔幾分鐘自動重新載入服務頁面", color = colors.TextDim, fontSize = 9.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Minus button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (config.autoRefreshMinutes > 1) colors.CardBgHover
                                    else colors.Border,
                                )
                                .clickable(enabled = config.autoRefreshMinutes > 1) {
                                    viewModel.setAutoRefreshMinutes(config.autoRefreshMinutes - 1)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "−",
                                color = if (config.autoRefreshMinutes > 1) colors.Text else colors.TextDim,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "${config.autoRefreshMinutes} 分",
                            color = colors.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(42.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        // Plus button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (config.autoRefreshMinutes < 10) colors.CardBgHover
                                    else colors.Border,
                                )
                                .clickable(enabled = config.autoRefreshMinutes < 10) {
                                    viewModel.setAutoRefreshMinutes(config.autoRefreshMinutes + 1)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+",
                                color = if (config.autoRefreshMinutes < 10) colors.Text else colors.TextDim,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── HTTP server ────────────────────────────────────────────────
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

            // ── Service accounts ───────────────────────────────────────────
            SectionTitle("服務帳號")
            // hint text
            Text(
                text = "▲▼ 調整顯示順序  •  開關控制是否在儀表板顯示  •  點右側「登入」進行帳號驗證",
                color = colors.TextDim,
                fontSize = 8.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            SettingsCard {
                val orderedKeys = config.effectiveServiceOrder()
                orderedKeys.forEachIndexed { index, key ->
                    val svc = config.services[key] ?: return@forEachIndexed
                    val isLoggedIn = config.authStatus[key]?.loggedIn == true
                    val isFirst = index == 0
                    val isLast = index == orderedKeys.lastIndex

                    if (index > 0) HorizontalDivider(color = colors.Border)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // ── Up / Down arrows ──────────────────────────────
                        Column(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconButton(
                                onClick = { viewModel.reorderService(key, -1) },
                                modifier = Modifier.size(22.dp),
                                enabled = !isFirst,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "上移",
                                    tint = if (!isFirst) colors.TextMuted else colors.Border,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(
                                onClick = { viewModel.reorderService(key, 1) },
                                modifier = Modifier.size(22.dp),
                                enabled = !isLast,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "下移",
                                    tint = if (!isLast) colors.TextMuted else colors.Border,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        // ── Accent dot ─────────────────────────────────────
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ServiceAccents.get(key)),
                        )

                        // ── Name + login status ───────────────────────────
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = svc.displayName,
                                color = if (svc.enabled) colors.Text else colors.TextDim,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isLoggedIn) "已登入" else "尚未登入",
                                color = if (isLoggedIn) colors.Success else colors.TextDim,
                                fontSize = 9.sp,
                            )
                        }

                        // ── Enable / disable toggle ────────────────────────
                        Switch(
                            checked = svc.enabled,
                            onCheckedChange = { viewModel.setServiceEnabled(key, it) },
                            modifier = Modifier.height(24.dp),
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = colors.Success,
                                uncheckedTrackColor = colors.Border,
                            ),
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // ── Login button ──────────────────────────────────
                        Text(
                            text = "登入 \u276F",
                            color = ServiceAccents.get(key),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onLoginService(key) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── About ──────────────────────────────────────────────────────
            SectionTitle("關於")
            SettingsCard {
                KvSettingsRow("版本", "2.0")
                HorizontalDivider(color = colors.Border)
                KvSettingsRow("資料來源", "WebView + HTTP Server")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Shared composables ──────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalAppColors.current
    Text(
        text = text.uppercase(),
        color = colors.TextDim,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.CardBg)
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
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = colors.Text, fontSize = 12.sp)
            if (sublabel.isNotEmpty()) {
                Text(text = sublabel, color = colors.TextDim, fontSize = 9.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.Success,
                uncheckedTrackColor = colors.Border,
            ),
        )
    }
}

@Composable
private fun KvSettingsRow(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = colors.TextDim, fontSize = 11.sp)
        Text(text = value, color = colors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(if (selected) colors.CardBgHover else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LayoutMiniPreview(id)
        Column(Modifier.weight(1f)) {
            Text(text = label, color = colors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = desc, color = colors.TextDim, fontSize = 9.sp)
        }
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) colors.Accent else Color.Transparent)
                .border(1.5.dp, if (selected) colors.Accent else colors.TextDim, CircleShape),
        )
    }
}

@Composable
private fun LayoutMiniPreview(variant: DashboardLayout) {
    val colors = LocalAppColors.current
    val clockColor = colors.Accent.copy(alpha = 0.65f)
    val cardColor = colors.BorderStrong

    Box(
        modifier = Modifier
            .width(42.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(colors.Bg),
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

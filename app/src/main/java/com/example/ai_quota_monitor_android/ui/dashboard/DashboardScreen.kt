package com.example.ai_quota_monitor_android.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.data.model.DashboardLayout
import com.example.ai_quota_monitor_android.ui.cards.ClockCardCompact
import com.example.ai_quota_monitor_android.ui.cards.ClockCardSidebar
import com.example.ai_quota_monitor_android.ui.cards.ClockCardStrip
import com.example.ai_quota_monitor_android.ui.cards.ServiceCard
import com.example.ai_quota_monitor_android.ui.components.StatusBar
import com.example.ai_quota_monitor_android.ui.theme.AppColors

/** Ordered service keys used by all layout variants. */
private val SERVICE_KEYS = listOf(
    "browser_claude_usage",
    "browser_github_copilot",
    "browser_openai",
    "browser_claude_billing",
    "browser_openrouter",
)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    when (state.config.dashboardLayout) {
        DashboardLayout.A -> LayoutA(state, isLandscape, viewModel, onSettingsClick)
        DashboardLayout.B -> LayoutB(state, isLandscape, viewModel, onSettingsClick)
        DashboardLayout.C -> LayoutC(state, isLandscape, viewModel, onSettingsClick)
        DashboardLayout.D -> LayoutD(state, isLandscape, viewModel, onSettingsClick)
    }
}

// ── Layout A: Clock compact + service grid ────────────────────────────────────
// Portrait:  [ClockCompact] / [Claude|GH] / [OA|API] / [OR]
// Landscape: [Clock|Claude|GH] / [OA|API|OR]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutA(
    state: DashboardUiState,
    isLandscape: Boolean,
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        topBar = { StandardTopBar(viewModel, onSettingsClick) },
        bottomBar = { BottomStatusBar(state) },
        containerColor = AppColors.Bg,
    ) { padding ->
        if (isLandscape) {
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClockCardCompact(Modifier.weight(0.85f))
                    SvcCard("browser_claude_usage", state, viewModel, Modifier.weight(1.3f))
                    SvcCard("browser_github_copilot", state, viewModel, Modifier.weight(1.3f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvcCard("browser_openai", state, viewModel, Modifier.weight(1f))
                    SvcCard("browser_claude_billing", state, viewModel, Modifier.weight(1f))
                    SvcCard("browser_openrouter", state, viewModel, Modifier.weight(1f))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClockCardCompact()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvcCard("browser_claude_usage", state, viewModel, Modifier.weight(1f))
                    SvcCard("browser_github_copilot", state, viewModel, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvcCard("browser_openai", state, viewModel, Modifier.weight(1f))
                    SvcCard("browser_claude_billing", state, viewModel, Modifier.weight(1f))
                }
                SvcCard("browser_openrouter", state, viewModel)
            }
        }
    }
}

// ── Layout B: Sidebar (landscape) / Banner (portrait) ────────────────────────
// Portrait:  [Brand + ClockStrip + icons] / single-col cards
// Landscape: [160dp Sidebar: brand+clock+status+icons] | [2-col grid]

@Composable
private fun LayoutB(
    state: DashboardUiState,
    isLandscape: Boolean,
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
) {
    Scaffold(containerColor = AppColors.Bg) { padding ->
        if (isLandscape) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                // Fixed sidebar
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight()
                        .background(AppColors.CardBg)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "AI Quota\nMonitor",
                        color = AppColors.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    ClockCardSidebar(Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\u25CF ",
                            color = if (state.serverRunning) AppColors.Success else AppColors.Error,
                            fontSize = 7.sp,
                        )
                        Text(
                            text = "HTTP :7890",
                            color = AppColors.TextDim,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        text = "${state.connectedServices} / ${state.config.services.size} 已連線",
                        color = AppColors.TextDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        IconButton(
                            onClick = { viewModel.refreshAll() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重新整理",
                                tint = AppColors.TextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "設定",
                                tint = AppColors.TextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Scrollable 2-col grid
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SvcCard("browser_claude_usage", state, viewModel, Modifier.weight(1f))
                        SvcCard("browser_github_copilot", state, viewModel, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SvcCard("browser_openai", state, viewModel, Modifier.weight(1f))
                        SvcCard("browser_claude_billing", state, viewModel, Modifier.weight(1f))
                    }
                    SvcCard("browser_openrouter", state, viewModel)
                }
            }
        } else {
            // Portrait: custom banner + scrollable single-col cards
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.CardBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "AI Quota\nMonitor",
                        color = AppColors.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    ClockCardStrip()
                    IconButton(onClick = { viewModel.refreshAll() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重新整理",
                            tint = AppColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "設定",
                            tint = AppColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SERVICE_KEYS.forEach { key -> SvcCard(key, state, viewModel) }
                }
                BottomStatusBar(state)
            }
        }
    }
}

// ── Layout C: Strip topbar with embedded clock ────────────────────────────────
// Both orientations: [Brand | divider | ClockStrip | (status) | ↻ | ⚙]
//                    2-col service grid below

@Composable
private fun LayoutC(
    state: DashboardUiState,
    isLandscape: Boolean,
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
) {
    Scaffold(containerColor = AppColors.Bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Strip header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.CardBg)
                    .padding(horizontal = 12.dp, vertical = if (isLandscape) 7.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "AI Quota Monitor",
                    color = AppColors.Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isLandscape) 16.sp else 14.sp,
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(AppColors.Border),
                )
                ClockCardStrip(Modifier.weight(1f))
                if (isLandscape) {
                    Text(
                        text = "${state.connectedServices}/${state.config.services.size}",
                        color = if (state.serverRunning) AppColors.Success else AppColors.TextDim,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                IconButton(onClick = { viewModel.refreshAll() }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "重新整理",
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "設定",
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 2-col card grid
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvcCard("browser_claude_usage", state, viewModel, Modifier.weight(1f))
                    SvcCard("browser_github_copilot", state, viewModel, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SvcCard("browser_openai", state, viewModel, Modifier.weight(1f))
                    SvcCard("browser_claude_billing", state, viewModel, Modifier.weight(1f))
                }
                SvcCard("browser_openrouter", state, viewModel)
            }
            BottomStatusBar(state)
        }
    }
}

// ── Layout D: Bento Mosaic ────────────────────────────────────────────────────
// Portrait:  [ClockCompact(0.7) | Claude] / GH / OA / API / OR
// Landscape: [Clock+OA (left 1/3)] | [Claude(full-right-top) / GH | API+OR(right-bottom)]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutD(
    state: DashboardUiState,
    isLandscape: Boolean,
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        topBar = { StandardTopBar(viewModel, onSettingsClick) },
        bottomBar = { BottomStatusBar(state) },
        containerColor = AppColors.Bg,
    ) { padding ->
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Left column: clock + OA
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ClockCardSidebar(Modifier.fillMaxWidth())
                    SvcCard("browser_openai", state, viewModel)
                }
                // Right area: Claude wide top + (GH | API+OR stacked)
                Column(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SvcCard("browser_claude_usage", state, viewModel)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SvcCard("browser_github_copilot", state, viewModel, Modifier.weight(1f))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SvcCard("browser_claude_billing", state, viewModel)
                            SvcCard("browser_openrouter", state, viewModel)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClockCardCompact(Modifier.weight(0.7f))
                    SvcCard("browser_claude_usage", state, viewModel, Modifier.weight(1f))
                }
                SvcCard("browser_github_copilot", state, viewModel)
                SvcCard("browser_openai", state, viewModel)
                SvcCard("browser_claude_billing", state, viewModel)
                SvcCard("browser_openrouter", state, viewModel)
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardTopBar(viewModel: DashboardViewModel, onSettingsClick: () -> Unit) {
    TopAppBar(
        title = { Text("AI Quota Monitor", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        actions = {
            IconButton(onClick = { viewModel.refreshAll() }) {
                Icon(Icons.Default.Refresh, contentDescription = "重新整理")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "設定")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppColors.Bg,
            titleContentColor = AppColors.Text,
            actionIconContentColor = AppColors.TextMuted,
        ),
    )
}

@Composable
private fun BottomStatusBar(state: DashboardUiState, modifier: Modifier = Modifier) {
    StatusBar(
        serverRunning = state.serverRunning,
        connectedServices = state.connectedServices,
        totalServices = state.config.services.size,
        modifier = modifier,
    )
}

@Composable
private fun SvcCard(
    key: String,
    state: DashboardUiState,
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val svc = state.config.services[key] ?: return
    ServiceCard(
        serviceKey = key,
        displayName = svc.displayName,
        result = state.results[key],
        collapsed = key in state.config.collapsedCards,
        onToggleCollapse = { viewModel.toggleCardCollapse(key) },
        modifier = modifier,
    )
}

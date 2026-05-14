package com.example.ai_quota_monitor_android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_quota_monitor_android.data.model.CardConfig
import com.example.ai_quota_monitor_android.data.model.SectionConfig
import com.example.ai_quota_monitor_android.data.model.ServiceResult
import com.example.ai_quota_monitor_android.ui.cards.ClockCard
import com.example.ai_quota_monitor_android.ui.cards.ServiceCard
import com.example.ai_quota_monitor_android.ui.components.StatusBar
import com.example.ai_quota_monitor_android.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Quota Monitor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                },
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
        },
        bottomBar = {
            StatusBar(
                serverRunning = state.serverRunning,
                connectedServices = state.connectedServices,
                totalServices = state.config.services.size,
            )
        },
        containerColor = AppColors.Bg,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.config.sections) { section ->
                SectionBlock(
                    section = section,
                    results = state.results,
                    services = state.config.services,
                )
            }
        }
    }
}

@Composable
private fun SectionBlock(
    section: SectionConfig,
    results: Map<String, ServiceResult>,
    services: Map<String, com.example.ai_quota_monitor_android.data.model.ServiceConfig>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section title
        if (section.cards.isNotEmpty()) {
            Text(
                text = section.name,
                color = AppColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        if (section.columns <= 1) {
            // Single column: render cards vertically
            section.cards.forEachIndexed { i, card ->
                if (i > 0) Spacer(modifier = Modifier.height(8.dp))
                CardSlot(card = card, results = results, services = services)
            }
        } else {
            // Multi-column grid
            val chunked = layoutCardsInRows(section.cards, section.columns)
            chunked.forEachIndexed { i, row ->
                if (i > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (card in row) {
                        val weight = card.span.toFloat() / section.columns
                        Box(modifier = Modifier.weight(weight)) {
                            CardSlot(card = card, results = results, services = services)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSlot(
    card: CardConfig,
    results: Map<String, ServiceResult>,
    services: Map<String, com.example.ai_quota_monitor_android.data.model.ServiceConfig>,
) {
    when (card.type) {
        "clock" -> ClockCard()
        "service" -> {
            val key = card.serviceKey ?: return
            val svc = services[key] ?: return
            ServiceCard(
                serviceKey = key,
                displayName = svc.displayName,
                result = results[key],
            )
        }
    }
}

/**
 * Distribute cards into rows based on column count and span.
 * E.g., 2-column section: cards with span=1 pair up, span=2 takes a full row.
 */
private fun layoutCardsInRows(cards: List<CardConfig>, columns: Int): List<List<CardConfig>> {
    val rows = mutableListOf<MutableList<CardConfig>>()
    var currentRow = mutableListOf<CardConfig>()
    var usedCols = 0

    for (card in cards) {
        val span = card.span.coerceIn(1, columns)
        if (usedCols + span > columns) {
            if (currentRow.isNotEmpty()) rows.add(currentRow)
            currentRow = mutableListOf()
            usedCols = 0
        }
        currentRow.add(card)
        usedCols += span
        if (usedCols >= columns) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            usedCols = 0
        }
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow)
    return rows
}

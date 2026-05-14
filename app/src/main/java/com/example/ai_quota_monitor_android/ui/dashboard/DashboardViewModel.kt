package com.example.ai_quota_monitor_android.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_quota_monitor_android.data.model.DashboardConfig
import com.example.ai_quota_monitor_android.data.model.ServiceResult
import com.example.ai_quota_monitor_android.data.repository.ConfigRepository
import com.example.ai_quota_monitor_android.data.repository.DataStoreRepository
import com.example.ai_quota_monitor_android.service.ALL_BROWSER_SERVICES
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val config: DashboardConfig = DashboardConfig(),
    val results: Map<String, ServiceResult> = emptyMap(),
    val serverRunning: Boolean = false,
    val connectedServices: Int = 0,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val configRepo = ConfigRepository(app)
    private val _ui = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = configRepo.load()
            _ui.value = _ui.value.copy(config = cfg)
        }
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                refreshAll()
                delay(_ui.value.config.autoRefreshMinutes * 60_000L)
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            val newResults = mutableMapOf<String, ServiceResult>()
            for ((key, svc) in ALL_BROWSER_SERVICES) {
                val result = svc.fetch()
                newResults[key] = result
            }
            val connected = newResults.count { it.value.success }
            _ui.value = _ui.value.copy(
                results = newResults,
                connectedServices = connected,
            )
        }
    }

    fun setServerRunning(running: Boolean) {
        _ui.value = _ui.value.copy(serverRunning = running)
    }

    fun updateConfig(config: DashboardConfig) {
        viewModelScope.launch {
            configRepo.save(config)
            _ui.value = _ui.value.copy(config = config)
        }
    }

    fun onDataReceived() {
        refreshAll()
    }
}

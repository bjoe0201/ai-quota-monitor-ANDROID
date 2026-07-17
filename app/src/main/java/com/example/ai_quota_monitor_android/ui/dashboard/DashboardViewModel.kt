package com.example.ai_quota_monitor_android.ui.dashboard

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_quota_monitor_android.data.model.DashboardConfig
import com.example.ai_quota_monitor_android.data.model.ServiceResult
import com.example.ai_quota_monitor_android.data.repository.ConfigRepository
import com.example.ai_quota_monitor_android.data.repository.DataStoreRepository
import com.example.ai_quota_monitor_android.service.ALL_BROWSER_SERVICES
import com.example.ai_quota_monitor_android.service.WebViewDataCollector
import com.example.ai_quota_monitor_android.data.model.effectiveServiceOrder
import kotlinx.coroutines.Job
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var collector: WebViewDataCollector? = null
    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            val cfg = configRepo.load()
            _ui.value = _ui.value.copy(config = cfg)
            // Start loading WebViews for logged-in services
            loadLoggedInServices(cfg)
        }
        observeDataStore()
        startPolling()
    }

    /**
     * Observe DataStoreRepository via SharedFlow for real-time updates.
     * SharedFlow has no equality-check conflation — every putData() call triggers a rebuild.
     * Errors inside rebuildResults() are caught so the coroutine never dies silently.
     */
    private fun observeDataStore() {
        viewModelScope.launch {
            DataStoreRepository.dataUpdateFlow.collect { _ ->
                try {
                    rebuildResults()
                } catch (e: Exception) {
                    // Never let an exception kill the observer coroutine
                }
            }
        }
    }

    private fun rebuildResults() {
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

    /**
     * Load service pages in background WebViews for all services that have auth.
     * WebView must be created on main thread.
     */
    private fun loadLoggedInServices(config: DashboardConfig) {
        mainHandler.post {
            if (collector == null) {
                collector = WebViewDataCollector(getApplication())
                collector!!.setOnSessionExpired { serviceKey ->
                    // Mark as logged out
                    viewModelScope.launch {
                        val current = _ui.value.config
                        val authMap = current.authStatus.toMutableMap()
                        authMap[serviceKey] = authMap[serviceKey]?.copy(loggedIn = false)
                            ?: com.example.ai_quota_monitor_android.data.model.AuthStatus(loggedIn = false)
                        updateConfig(current.copy(authStatus = authMap))
                    }
                }
            }

            // Start collectors in the same order as cards are displayed in Settings/Dashboard.
            // Disabled cards must not consume a background WebView.
            for (key in config.effectiveServiceOrder()) {
                val svc = config.services[key] ?: continue
                if (!svc.enabled) continue
                val auth = config.authStatus[key]
                if (auth?.loggedIn == true && svc.url.isNotEmpty()) {
                    collector!!.loadService(key, svc.url)
                    // GitHub Copilot: also load billing/budgets in a parallel WebView
                    // so both pages send data to the same github_copilot DataStore key
                    if (key == "browser_github_copilot") {
                        collector!!.loadService(
                            "browser_github_copilot_budgets",
                            "https://github.com/settings/billing/budgets",
                        )
                    }
                }
            }
        }
    }

    /**
     * Polling loop: refresh WebViews periodically.
     * Does NOT delay before the first cycle so data is loaded as soon as possible.
     */
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                val intervalMs = _ui.value.config.autoRefreshMinutes.coerceAtLeast(1) * 60_000L
                delay(intervalMs)
                refreshAll()
            }
        }
    }

    fun refreshAll() {
        // Reload WebViews for logged-in services (triggers fresh JS injection)
        loadLoggedInServices(_ui.value.config)
        // Also rebuild from current store data (in case HTTP server got data)
        rebuildResults()
    }

    /**
     * Lightweight refresh: rebuild UI from current DataStore without reloading WebViews.
     * Call this from Activity.onResume() to ensure stale UI is never shown.
     */
    fun refreshFromStore() {
        rebuildResults()
    }

    fun setServerRunning(running: Boolean) {
        _ui.value = _ui.value.copy(serverRunning = running)
    }

    fun toggleCardCollapse(cardKey: String) {
        val current = _ui.value.config
        val newSet = if (cardKey in current.collapsedCards)
            current.collapsedCards - cardKey
        else
            current.collapsedCards + cardKey
        updateConfig(current.copy(collapsedCards = newSet))
    }

    fun updateConfig(config: DashboardConfig) {
        viewModelScope.launch {
            configRepo.save(config)
            _ui.value = _ui.value.copy(config = config)
        }
    }

    /** Move a service card up (−1) or down (+1) in the display order. */
    fun reorderService(key: String, direction: Int) {
        val config = _ui.value.config
        val order = config.effectiveServiceOrder().toMutableList()
        val idx = order.indexOf(key)
        if (idx < 0) return
        val newIdx = (idx + direction).coerceIn(0, order.size - 1)
        if (newIdx == idx) return
        order.removeAt(idx)
        order.add(newIdx, key)
        updateConfig(config.copy(serviceOrder = order))
    }

    /** Enable or disable a service card (display + background fetch). */
    fun setServiceEnabled(key: String, enabled: Boolean) {
        val config = _ui.value.config
        val svc = config.services[key] ?: return
        val newServices = config.services.toMutableMap()
        newServices[key] = svc.copy(enabled = enabled)
        updateConfig(config.copy(services = newServices))
    }

    /** Set the WebView auto-refresh interval (clamped to 1–10 minutes). */
    fun setAutoRefreshMinutes(minutes: Int) {
        updateConfig(_ui.value.config.copy(autoRefreshMinutes = minutes.coerceIn(1, 10)))
        // Restart the polling loop so the new interval takes effect immediately,
        // and trigger a service reload right away.
        refreshAll()
        startPolling()
    }

    /**
     * Called after user completes login for a service.
     * Immediately loads the service page to start collecting data.
     */
    fun onServiceLoggedIn(serviceKey: String) {
        val config = _ui.value.config
        val svc = config.services[serviceKey] ?: return
        if (svc.url.isNotEmpty()) {
            mainHandler.post {
                collector?.loadService(serviceKey, svc.url)
            }
        }
    }

    override fun onCleared() {
        mainHandler.post { collector?.destroyAll() }
        super.onCleared()
    }
}

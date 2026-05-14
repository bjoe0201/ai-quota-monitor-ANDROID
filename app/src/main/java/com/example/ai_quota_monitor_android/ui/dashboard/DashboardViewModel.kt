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
import com.example.ai_quota_monitor_android.service.BROWSER_SERVICE_SOURCES
import com.example.ai_quota_monitor_android.service.WebViewDataCollector
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
     * Observe DataStoreRepository for real-time updates from WebView JS or HTTP server.
     * Whenever data changes, rebuild ServiceResults for all services.
     */
    private fun observeDataStore() {
        viewModelScope.launch {
            DataStoreRepository.store.collect { storeData ->
                rebuildResults(storeData)
            }
        }
    }

    private fun rebuildResults(storeData: Map<String, Map<String, Any?>> = DataStoreRepository.getAllData()) {
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

            for ((key, svc) in config.services) {
                val auth = config.authStatus[key]
                if (auth?.loggedIn == true && svc.url.isNotEmpty()) {
                    collector!!.loadService(key, svc.url)
                }
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(_ui.value.config.autoRefreshMinutes * 60_000L)
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

    fun setServerRunning(running: Boolean) {
        _ui.value = _ui.value.copy(serverRunning = running)
    }

    fun updateConfig(config: DashboardConfig) {
        viewModelScope.launch {
            configRepo.save(config)
            _ui.value = _ui.value.copy(config = config)
        }
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

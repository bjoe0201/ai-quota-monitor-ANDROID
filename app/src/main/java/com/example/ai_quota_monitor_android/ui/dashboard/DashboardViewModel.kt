package com.example.ai_quota_monitor_android.ui.dashboard

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_quota_monitor_android.data.model.DashboardConfig
import com.example.ai_quota_monitor_android.data.model.ServiceResult
import com.example.ai_quota_monitor_android.data.repository.ConfigRepository
import com.example.ai_quota_monitor_android.data.repository.DataStoreRepository
import com.example.ai_quota_monitor_android.service.ALL_BROWSER_SERVICES
import com.example.ai_quota_monitor_android.service.CollectionStep
import com.example.ai_quota_monitor_android.service.RendererRecoveryPolicy
import com.example.ai_quota_monitor_android.service.WebViewDataCollector
import com.example.ai_quota_monitor_android.service.collectionSteps
import com.example.ai_quota_monitor_android.data.model.effectiveServiceOrder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    private var collector: WebViewDataCollector? = null
    private var cycleJob: Job? = null
    private var stepJob: Job? = null
    private val recoveryPolicy = RendererRecoveryPolicy { SystemClock.elapsedRealtime() }
    private var rendererDiedInStep = false

    init {
        viewModelScope.launch {
            val cfg = configRepo.load()
            _ui.value = _ui.value.copy(config = cfg)
            startCollectionLoop()
        }
        observeDataStore()
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
     * WebViews must be created on the main thread; [viewModelScope] already runs there.
     */
    private fun ensureCollector(): WebViewDataCollector {
        collector?.let { return it }
        val created = WebViewDataCollector(getApplication())
        created.setOnSessionExpired { serviceKey -> markLoggedOut(serviceKey) }
        created.setOnRendererGone {
            // The page this step was waiting on no longer exists — stop waiting out its timeout.
            rendererDiedInStep = true
            stepJob?.cancel()
        }
        collector = created
        return created
    }

    private fun markLoggedOut(serviceKey: String) {
        val current = _ui.value.config
        val authMap = current.authStatus.toMutableMap()
        authMap[serviceKey] = authMap[serviceKey]?.copy(loggedIn = false)
            ?: com.example.ai_quota_monitor_android.data.model.AuthStatus(loggedIn = false)
        updateConfig(current.copy(authStatus = authMap))
    }

    /**
     * Collection loop: run one cycle, then idle for the refresh interval and run the next.
     *
     * The interval is measured *after* a cycle finishes, so cycles can never overlap and pile
     * pages on top of each other. Starts immediately — no delay before the first cycle.
     */
    private fun startCollectionLoop() {
        cycleJob?.cancel()
        stepJob = null
        // A cancelled cycle may have left a page loaded; never keep more than one alive.
        collector?.destroyAll()
        cycleJob = viewModelScope.launch {
            while (true) {
                runCollectionCycle()
                delay(_ui.value.config.autoRefreshMinutes.coerceAtLeast(1) * 60_000L)
            }
        }
    }

    /**
     * Walk the collection plan one page at a time, tearing each page down before the next.
     *
     * This sequencing is the whole point: six desktop SPAs alive together pushed the shared
     * renderer process past 1.5 GB until it was killed, which took the app with it. See
     * `docs/01-webview-renderer-oom-crash.md`.
     */
    private suspend fun runCollectionCycle() = coroutineScope {
        val activeCollector = ensureCollector()
        for (step in collectionSteps(_ui.value.config)) {
            rendererDiedInStep = false
            // A child of the cycle: cancelling the cycle cancels the page it is waiting on,
            // while cancelling one step only moves the cycle on to the next.
            val job = launch { collectStep(activeCollector, step) }
            stepJob = job
            job.join()
            stepJob = null
            activeCollector.destroyService(step.serviceKey)
            if (rendererDiedInStep) {
                // Don't walk straight into the next page while the renderer is still unhealthy.
                delay(recoveryPolicy.scheduleFor(1).first())
            }
        }
        rebuildResults()
    }

    /**
     * Load one page and keep it only as long as it is still producing data: wait for the first
     * update, then until the page has been quiet for [SETTLE_MS]. [PAGE_TIMEOUT_MS] caps the
     * whole step, for pages that never report anything.
     *
     * The quiet window matters because some pages report in stages — OpenRouter, for instance,
     * sends its balance and then navigates to `/activity` for the usage figures.
     */
    private suspend fun collectStep(
        activeCollector: WebViewDataCollector,
        step: CollectionStep,
    ) = coroutineScope {
        val updates = Channel<Unit>(Channel.CONFLATED)
        // UNDISPATCHED: subscribe before the page starts loading, so no update is missed.
        val subscription = launch(start = CoroutineStart.UNDISPATCHED) {
            DataStoreRepository.dataUpdateFlow.collect {
                if (it == step.sourceKey) updates.trySend(Unit)
            }
        }
        try {
            activeCollector.loadService(step.serviceKey, step.url)
            val deadline = SystemClock.elapsedRealtime() + PAGE_TIMEOUT_MS
            var gotData = false
            while (true) {
                val budget = deadline - SystemClock.elapsedRealtime()
                if (budget <= 0) break
                val wait = if (gotData) minOf(SETTLE_MS, budget) else budget
                withTimeoutOrNull(wait) { updates.receive() } ?: break
                gotData = true
            }
        } finally {
            subscription.cancel()
        }
    }

    /** Restart collection now: cancels the current cycle and starts a fresh one. */
    fun refreshAll() {
        startCollectionLoop()
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
        // Restart the loop so the new interval takes effect immediately.
        refreshAll()
    }

    /**
     * Called after the user completes login for a service. Restarts collection so the newly
     * authorised service is picked up in this cycle.
     */
    fun onServiceLoggedIn(serviceKey: String) {
        if (_ui.value.config.services[serviceKey]?.url?.isNotEmpty() != true) return
        startCollectionLoop()
    }

    override fun onCleared() {
        cycleJob?.cancel()
        stepJob = null
        collector?.destroyAll()
        super.onCleared()
    }
}

/** Hard cap for one page; some SPAs need 30–60 s before they report anything. */
private const val PAGE_TIMEOUT_MS = 90_000L

/** How long a page may stay quiet after reporting before it is considered done. */
private const val SETTLE_MS = 20_000L

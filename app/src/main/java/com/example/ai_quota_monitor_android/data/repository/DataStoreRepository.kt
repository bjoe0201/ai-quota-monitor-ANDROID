package com.example.ai_quota_monitor_android.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * In-memory data store for service data received from WebView or HTTP server.
 * Mirrors Python local_server.DATA_STORE.
 *
 * Two notification channels:
 *  - [store]          : StateFlow of the full data map (for observers wanting the full picture)
 *  - [dataUpdateFlow] : SharedFlow that emits the sourceKey whenever putData() is called.
 *                       Use this for immediate UI refresh — it has no equality-check conflation.
 */
object DataStoreRepository {

    private val _store = MutableStateFlow<Map<String, Map<String, Any?>>>(emptyMap())
    val store: StateFlow<Map<String, Map<String, Any?>>> = _store.asStateFlow()

    /** Emits the sourceKey every time new data is stored. No conflation — never missed. */
    private val _dataUpdateFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val dataUpdateFlow: SharedFlow<String> = _dataUpdateFlow.asSharedFlow()

    private var _refreshSeq = 0

    fun putData(sourceKey: String, data: Map<String, Any?>) {
        _store.update { current ->
            val existing = current[sourceKey].orEmpty()
            val merged = existing + data + ("received_at" to Instant.now().toString())
            current + (sourceKey to merged)
        }
        // Notify via SharedFlow immediately (no conflation / equality checks)
        _dataUpdateFlow.tryEmit(sourceKey)
    }

    fun getData(sourceKey: String): Map<String, Any?>? = _store.value[sourceKey]

    fun getAllData(): Map<String, Map<String, Any?>> = _store.value

    fun requestRefresh(): Int {
        _refreshSeq++
        return _refreshSeq
    }

    fun getRefreshSeq(): Int = _refreshSeq

    fun clear() {
        _store.value = emptyMap()
    }
}

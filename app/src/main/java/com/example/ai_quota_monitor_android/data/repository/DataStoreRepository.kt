package com.example.ai_quota_monitor_android.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * In-memory data store for service data received from WebView or HTTP server.
 * Mirrors Python local_server.DATA_STORE.
 */
object DataStoreRepository {

    private val _store = MutableStateFlow<Map<String, Map<String, Any?>>>(emptyMap())
    val store: StateFlow<Map<String, Map<String, Any?>>> = _store.asStateFlow()

    private var _refreshSeq = 0

    fun putData(sourceKey: String, data: Map<String, Any?>) {
        _store.update { current ->
            val existing = current[sourceKey].orEmpty()
            val merged = existing + data + ("received_at" to Instant.now().toString())
            current + (sourceKey to merged)
        }
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

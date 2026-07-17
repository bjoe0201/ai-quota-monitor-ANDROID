package com.example.ai_quota_monitor_android.data.repository

import android.content.Context
import com.example.ai_quota_monitor_android.data.model.AuthStatus
import com.example.ai_quota_monitor_android.data.model.DashboardConfig
import com.example.ai_quota_monitor_android.data.model.defaultServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

class ConfigRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val configFile: File get() = File(context.filesDir, "config.json")

    private val _config = MutableStateFlow(DashboardConfig())
    val config: StateFlow<DashboardConfig> = _config.asStateFlow()

    fun load(): DashboardConfig {
        val file = configFile
        if (!file.exists()) {
            _config.value = DashboardConfig()
            save()
            return _config.value
        }
        val loaded = try {
            json.decodeFromString<DashboardConfig>(file.readText())
        } catch (_: Exception) {
            DashboardConfig()
        }
        // Preserve user settings while adding newly introduced services on upgrade.
        val defaults = defaultServices()
        val mergedServices = defaults + loaded.services
        val mergedOrder = (loaded.serviceOrder + defaults.keys).distinct()
            .filter { it in mergedServices }
        val merged = loaded.copy(services = mergedServices, serviceOrder = mergedOrder)
        _config.value = merged
        if (merged != loaded) save()
        return merged
    }

    fun save(config: DashboardConfig? = null) {
        if (config != null) _config.value = config
        configFile.writeText(json.encodeToString(DashboardConfig.serializer(), _config.value))
    }

    fun update(transform: (DashboardConfig) -> DashboardConfig) {
        _config.value = transform(_config.value)
        save()
    }

    fun updateAuthStatus(serviceKey: String, status: AuthStatus) {
        update { config ->
            config.copy(authStatus = config.authStatus + (serviceKey to status))
        }
    }

    fun getAuthStatus(serviceKey: String): AuthStatus? = _config.value.authStatus[serviceKey]
}

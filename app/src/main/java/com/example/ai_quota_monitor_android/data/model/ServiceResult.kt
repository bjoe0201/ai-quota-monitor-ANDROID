package com.example.ai_quota_monitor_android.data.model

data class ServiceResult(
    val serviceName: String,
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null,
)

package com.example.ai_quota_monitor_android.service

import com.example.ai_quota_monitor_android.data.model.ServiceResult

abstract class BaseService {
    abstract val name: String
    abstract val sourceKey: String

    abstract fun fetch(): ServiceResult

    protected fun notConnected(): ServiceResult = ServiceResult(
        serviceName = name,
        success = false,
        error = "等待瀏覽器連線...\n請在瀏覽器開啟對應頁面（已安裝 Tampermonkey 腳本）",
    )

    protected fun error(msg: String): ServiceResult = ServiceResult(
        serviceName = name,
        success = false,
        error = msg,
    )
}

package com.example.ai_quota_monitor_android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ai_quota_monitor_android.service.MonitorForegroundService
import com.example.ai_quota_monitor_android.ui.dashboard.DashboardScreen
import com.example.ai_quota_monitor_android.ui.dashboard.DashboardViewModel
import com.example.ai_quota_monitor_android.ui.settings.ServiceLoginScreen
import com.example.ai_quota_monitor_android.ui.settings.SettingsScreen
import com.example.ai_quota_monitor_android.ui.theme.AiQuotaMonitorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startMonitorService()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            AiQuotaMonitorTheme(themeMode = uiState.config.themeMode) {
                // Simple stack-based navigation
                var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

                when (val s = screen) {
                    Screen.Dashboard -> DashboardScreen(
                        viewModel = viewModel,
                        onSettingsClick = { screen = Screen.Settings },
                    )
                    Screen.Settings -> SettingsScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.Dashboard },
                        onLoginService = { key -> screen = Screen.Login(key) },
                    )
                    is Screen.Login -> ServiceLoginScreen(
                        serviceKey = s.serviceKey,
                        viewModel = viewModel,
                        onBack = { screen = Screen.Settings },
                    )
                }
            }
        }
    }

    /**
     * Rebuild UI from current DataStore whenever the Activity comes to foreground.
     * This ensures stale data is never shown after returning from background.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshFromStore()
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitorForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        viewModel.setServerRunning(true)
    }
}

private sealed class Screen {
    data object Dashboard : Screen()
    data object Settings : Screen()
    data class Login(val serviceKey: String) : Screen()
}

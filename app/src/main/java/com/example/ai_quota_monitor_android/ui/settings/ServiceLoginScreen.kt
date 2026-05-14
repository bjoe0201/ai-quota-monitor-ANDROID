package com.example.ai_quota_monitor_android.ui.settings

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai_quota_monitor_android.data.model.AuthStatus
import com.example.ai_quota_monitor_android.ui.dashboard.DashboardViewModel
import com.example.ai_quota_monitor_android.ui.theme.AppColors
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ServiceLoginScreen(
    serviceKey: String,
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val svc = state.config.services[serviceKey]
    val displayName = svc?.displayName ?: serviceKey
    val url = svc?.url ?: ""

    var progress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(url) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = currentUrl.take(60),
                            color = AppColors.TextDim,
                            fontSize = 8.sp,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Mark as logged in and save cookies
                        CookieManager.getInstance().flush()
                        val updated = state.config.copy(
                            authStatus = state.config.authStatus + (serviceKey to AuthStatus(
                                loggedIn = true,
                                lastLogin = Instant.now().toString(),
                            ))
                        )
                        viewModel.updateConfig(updated)
                        onBack()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "完成登入", tint = AppColors.Success)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Bg,
                    titleContentColor = AppColors.Text,
                    navigationIconContentColor = AppColors.TextMuted,
                ),
            )
        },
        containerColor = AppColors.Bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.Info,
                    trackColor = AppColors.Border,
                )
            }

            if (url.isNotEmpty()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                            }
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishUrl: String?) {
                                    super.onPageFinished(view, finishUrl)
                                    finishUrl?.let { currentUrl = it }
                                    CookieManager.getInstance().flush()
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

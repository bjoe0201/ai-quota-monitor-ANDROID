package com.example.ai_quota_monitor_android.ui.settings

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/125.0.6422.176 Safari/537.36"

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
    val loginUrl = svc?.loginUrl?.takeIf { it.isNotEmpty() } ?: svc?.url ?: ""

    var progress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(loginUrl) }
    var googleSsoBlocked by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = currentUrl.take(80),
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
                        CookieManager.getInstance().flush()
                        val updated = state.config.copy(
                            authStatus = state.config.authStatus + (serviceKey to AuthStatus(
                                loggedIn = true,
                                lastLogin = Instant.now().toString(),
                            ))
                        )
                        viewModel.updateConfig(updated)
                        viewModel.onServiceLoggedIn(serviceKey)
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

            // Google SSO blocked banner
            if (googleSsoBlocked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .border(1.dp, AppColors.Warning, RoundedCornerShape(8.dp))
                        .background(AppColors.CardBg, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Google 登入不支援內嵌瀏覽器",
                        color = AppColors.Warning,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "此服務僅支援 Google SSO 登入，但 Google 禁止在應用內 WebView 中使用。\n\n" +
                                "替代方案：\n" +
                                "1. 在電腦瀏覽器安裝 Tampermonkey 腳本\n" +
                                "2. 開啟對應的服務頁面\n" +
                                "3. 腳本會自動透過 HTTP 將資料傳送到本 App（port 7890）\n\n" +
                                "請確認手機與電腦在同一網路下。",
                        color = AppColors.TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            if (loginUrl.isNotEmpty()) {
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
                                @Suppress("DEPRECATION")
                                databaseEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = DESKTOP_UA
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(false)
                            }
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishUrl: String?) {
                                    super.onPageFinished(view, finishUrl)
                                    finishUrl?.let {
                                        currentUrl = it
                                        // Detect Google SSO block
                                        if (it.contains("accounts.google.com/gsi/") ||
                                            it.contains("accounts.google.com/o/oauth2/") ||
                                            it.contains("accounts.google.com/signin/oauth")
                                        ) {
                                            googleSsoBlocked = true
                                        }
                                    }
                                    CookieManager.getInstance().flush()
                                }
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean = false
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            loadUrl(loginUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (googleSsoBlocked) Modifier.height(0.dp) else Modifier),
                )
            }
        }
    }
}

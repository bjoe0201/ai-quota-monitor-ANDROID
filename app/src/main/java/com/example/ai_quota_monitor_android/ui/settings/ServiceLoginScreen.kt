package com.example.ai_quota_monitor_android.ui.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.ai_quota_monitor_android.service.WebViewDataCollector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai_quota_monitor_android.data.model.AuthStatus
import com.example.ai_quota_monitor_android.ui.dashboard.DashboardViewModel
import com.example.ai_quota_monitor_android.ui.theme.LocalAppColors
import java.time.Instant

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/136.0.0.0 Safari/537.36"

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
    val colors = LocalAppColors.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = currentUrl.take(80),
                            color = colors.TextDim,
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
                        Icon(Icons.Default.Check, contentDescription = "完成登入", tint = colors.Success)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.Bg,
                    titleContentColor = colors.Text,
                    navigationIconContentColor = colors.TextMuted,
                ),
            )
        },
        containerColor = colors.Bg,
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
                    color = colors.Info,
                    trackColor = colors.Border,
                )
            }

            // Google SSO blocked banner
            if (googleSsoBlocked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .border(1.dp, colors.Warning, RoundedCornerShape(8.dp))
                        .background(colors.CardBg, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Google 登入在 App 內無法使用",
                        color = colors.Warning,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Google 禁止在應用內 WebView 使用 SSO。\n" +
                                "請往下捲動，改用 Email 登入。\n\n" +
                                "若此服務無 Email 選項，請使用 Tampermonkey：\n" +
                                "1. 在電腦瀏覽器安裝 Tampermonkey 腳本\n" +
                                "2. 開啟對應的服務頁面（port 7890）",
                        color = colors.TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "在外部瀏覽器開啟 \u2197",
                        color = colors.Info,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.Info.copy(alpha = 0.12f))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                            // Suppress X-Requested-With header so Google OAuth
                            // does not detect this as an embedded WebView.
                            WebViewDataCollector.suppressRequestedWithHeader(this)
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishUrl: String?) {
                                    super.onPageFinished(view, finishUrl)
                                    finishUrl?.let {
                                        currentUrl = it
                                        // Only /gsi/ is the actual dead-end. The standard
                                        // OAuth flow (/v3/signin/, /signin/oauth/) works
                                        // fine in WebView so we must NOT block it.
                                        if (it.contains("accounts.google.com/gsi/")) {
                                            googleSsoBlocked = true
                                            // Navigate back to login page so user can
                                            // try the email login option instead.
                                            view?.postDelayed({
                                                view?.loadUrl(loginUrl)
                                            }, 800)
                                        }
                                        // If we reached the actual service page (not /login),
                                        // login succeeded — clear the banner.
                                        val targetDomain = svc?.url?.let { u ->
                                            Uri.parse(u).host
                                        }
                                        if (targetDomain != null &&
                                            it.contains(targetDomain) &&
                                            !it.contains("/login")) {
                                            googleSsoBlocked = false
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

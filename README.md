<p align="center">
  <img src="docs/app-icon.svg" width="128" height="128" alt="AI Quota Monitor icon">
</p>

# AI Quota Monitor — Android

Android port of [ai-quota-monitor](https://github.com/bjoe0201/ai-quota-monitor) — a desktop widget that monitors AI service quotas. The original is Python + tkinter; this project rebuilds it as a native Android app with Kotlin + Jetpack Compose + Material3.

## Features

- **5 AI 服務即時監控** — Claude.ai、GitHub Copilot、OpenAI、Claude API、OpenRouter
- **Home Assistant 風格卡片** — 可配置的多欄 section/card 佈局，支援展開/收合（狀態持久化）
- **雙資料來源**
  - WebView + JS 注入：App 內登入後自動載入服務頁面，攔截 API 回應
  - HTTP Server (port 7890)：接收 PC 瀏覽器 Tampermonkey 腳本推送的資料
- **翻頁時鐘卡片** — 移植自桌面版的 FlipClock 風格
- **Dark Theme** — Linear/Raycast 風格深色主題
- **Cookie 持久化** — 登入一次，重啟 App 不需要再登入
- **Google SSO 偵測** — 自動偵測 Google SSO 限制並提示替代方案

## Screenshots

| Dashboard | Settings |
|-----------|----------|
| (TODO) | (TODO) |

## Architecture

```
資料來源                          資料儲存                    UI
┌──────────────────┐
│ WebView + JS注入  │──┐
│ (背景載入服務頁面)   │  │     ┌──────────────────┐    ┌──────────────┐
└──────────────────┘  ├────▶│ DataStoreRepository │───▶│ DashboardScreen │
┌──────────────────┐  │     │   (記憶體 StateFlow)  │    │  ServiceCard  │
│ HTTP Server :7890 │──┘     └──────────────────┘    │  ClockCard    │
│ (Tampermonkey)    │                                 └──────────────┘
└──────────────────┘
```

## Monitored Services

| Service | Source Key | Login URL | Data URL |
|---------|-----------|-----------|----------|
| Claude.ai | `claude_usage` | claude.ai/login | claude.ai/settings/usage |
| GitHub Copilot | `github_copilot` | github.com/login | github.com/settings/copilot |
| OpenAI | `openai_billing` | platform.openai.com/login | platform.openai.com/settings/.../billing |
| Claude API | `claude_billing` | platform.claude.com/login | platform.claude.com/settings/billing |
| OpenRouter | `openrouter` | openrouter.ai | openrouter.ai/settings/credits + /activity |

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Tests
./gradlew test
```

Windows: use `gradlew.bat` instead of `./gradlew`.

## Requirements

- Android 12+ (minSdk 31)
- 手機與電腦在同一 Wi-Fi（若使用 Tampermonkey 推送）

## Tech Stack

- Kotlin + Jetpack Compose + Material3
- NanoHTTPD (embedded HTTP server)
- kotlinx.serialization (config persistence)
- AndroidX Navigation Compose
- AGP 9.1.1 | Kotlin 2.2.10 | Compose BOM 2026.02.01

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android port of [ai-quota-monitor](https://github.com/bjoe0201/ai-quota-monitor) — a desktop widget that monitors AI service quotas (OpenAI, Claude, GitHub Copilot, OpenRouter). The original is Python + tkinter; this project rebuilds it as a native Android app with Kotlin + Jetpack Compose.

Full migration plan: `PLANS/migration-plan.md`

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests (JVM, no device needed)
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.example.ai_quota_monitor_android.ExampleUnitTest"

# Run instrumented tests (requires connected device or emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Architecture

Single-module Android app (`:app`) with Jetpack Compose + Material3.

### Module Layout (target)

```
app/src/main/java/com/example/ai_quota_monitor_android/
├── data/
│   ├── model/          # ServiceResult, DashboardConfig, ServiceConfig
│   ├── repository/     # DataStoreRepository (in-memory), ConfigRepository (JSON)
│   └── server/         # LocalHttpServer (NanoHTTPD, port 7890)
├── service/
│   ├── BaseService.kt          # Abstract: fetch(config) -> ServiceResult
│   ├── BrowserDataService.kt   # 5 subclasses reading from DataStoreRepository
│   ├── WebViewDataCollector.kt  # WebView JS injection + Cookie management
│   └── MonitorForegroundService.kt
├── ui/
│   ├── theme/          # Dark theme (Linear/Raycast style from original COLORS dict)
│   ├── dashboard/      # DashboardScreen, ViewModel, CardGridLayout
│   ├── cards/          # ServiceCard, ClockCard, HeroSection, SegmentedProgressBar, KvRow, BarRow
│   ├── settings/       # SettingsScreen, ServiceLoginScreen (full-screen WebView login)
│   └── components/     # StatusBar
└── util/               # FormatUtils, ColorUtils
```

### Data Flow

Two data paths feed into the same `DataStoreRepository`:

1. **WebView path (primary):** App loads AI service pages in background WebView, injects JS to intercept API responses via `@JavascriptInterface`, data flows directly into repository.
2. **HTTP Server path (secondary):** NanoHTTPD on port 7890 receives POST `/update` from PC browser running Tampermonkey script (same protocol as original Python app).

### Authentication

All AI service pages require user login. Users authenticate once via full-screen WebView in the app; cookies are persisted by `CookieManager` to disk. Background fetches reuse saved cookies. If a session expires (detected by redirect to login URL), the card shows a warning and prompts re-login.

### 5 Monitored Services

| Service | source_key | Data Source URL |
|---------|-----------|-----------------|
| Claude.ai | `claude_usage` | claude.ai/settings/usage |
| GitHub Copilot | `github_copilot` | github.com/settings/billing |
| OpenAI | `openai_billing` | platform.openai.com/billing |
| Claude API | `claude_billing` | platform.claude.com/settings/billing |
| OpenRouter | `openrouter` | openrouter.ai/settings/credits + /activity |

### Card System (Home Assistant style)

Dashboard is divided into configurable **Sections**, each containing **Cards**. Layout is defined in `config.json`:
- `sections[].columns` — 1 or 2 columns per section
- `sections[].cards[].span` — how many columns a card occupies
- `services.<key>.display_name` — customizable card titles
- `services.<key>.enabled` — show/hide individual services
- `collapsedCards` — persisted set of collapsed card keys

### Key Versions

- AGP: 9.1.1 | Kotlin: 2.2.10 | Compose BOM: 2026.02.01
- `minSdk = 31`, `targetSdk = 36`
- Dependencies managed via `gradle/libs.versions.toml` (version catalog)

## Conventions

### Language

- Code: Kotlin (English identifiers, English comments)
- UI strings: Traditional Chinese (zh-TW), matching the original project
- Config keys: English snake_case

### Theme Colors

All colors ported from the original `gui/widgets.py` COLORS dict (Linear/Raycast dark theme). Each service has an accent color defined in `ServiceAccents` for header tinting. Use `ColorUtils.headerTint()` to blend accent with card background.

### Adding a New Service

1. Add a `BaseService` subclass in `service/` with `fetch(config) -> ServiceResult`
2. Add default config entry in `ConfigRepository` default config
3. Add source key mapping in `DataStoreRepository`
4. Add card rendering branch in `ServiceCard` composable (Hero/Bar/KV formatting)
5. Add login URL detection in `WebViewDataCollector.isLoginPage()`

### Config File

Location: App internal storage `files/config.json`. Sensitive fields (tokens, cookies) are Base64-encoded on disk (not encrypted), matching the original project's approach. `ConfigRepository.load()` merges saved config with defaults to handle new keys across versions.

### Threading Model

- WebView operations: Main thread only
- HTTP server: runs in Foreground Service on daemon thread
- Service fetch calls: Coroutines (viewModelScope)
- UI updates: Compose StateFlow, collected on main thread

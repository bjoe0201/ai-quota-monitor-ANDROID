# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

This is a single-module Android app (`:app`) built with **Jetpack Compose** and **Material3**.

- **Entry point:** `MainActivity` — sets up the Compose content tree with edge-to-edge display
- **Theme:** `ui/theme/` — `Theme.kt` (dynamic color + dark/light), `Color.kt`, `Type.kt`
- **Package:** `com.example.ai_quota_monitor_android`

### Key versions
- AGP: 9.1.1 | Kotlin: 2.2.10 | Compose BOM: 2026.02.01
- `minSdk = 31`, `targetSdk = 36`
- All dependency versions managed via `gradle/libs.versions.toml` (version catalog)

## Project Status

This project is at initial scaffolding stage (Android Studio template). The app name "ai-quota-monitor" indicates the intended purpose is monitoring AI API quotas, but the core feature implementation has not yet been built.

# Changelog

## v1.3 (2026-05-15)

### Features
- Add Dark / Light theme toggle (淺色模式) in Settings
- New app icon: multi-color gauge meter with monochrome adaptive-icon layer

### UI
- README revamp with full screenshot gallery (7 screenshots)
- Add PICS directory with annotated screenshots for documentation

### Bug Fixes
- versionCode / versionName now properly synced with release tag

---

## v1.2 (2026-05-15)

### Redesign
- New app icon: multi-color gauge meter representing the 5 AI service monitors
  - Each arc segment uses the service's accent color (green/blue/violet/purple/indigo)
  - Gauge needle + AI sparkle accent on dark background
  - Dedicated monochrome layer for Android 13+ themed icons
  - Removed default Android robot webp fallbacks (minSdk 31 always uses adaptive icon)

## v1.1 (2026-05-15)

### Bug Fixes
- Fix Google SSO detection — only block on actual dead-end `/gsi/` URL, not normal OAuth flow
- Fix Claude API URLs — update from `console.anthropic.com` to `platform.claude.com`
- Fix Claude API loginUrl — use `/login` path for proper login page
- Fix OpenRouter balance parsing — use `aria-label` attribute instead of animated counter text
- Fix OpenRouter incomplete data — auto-navigate from `/settings/credits` to `/activity` for full data

### Features
- Persist card collapse state across app restarts (stored in `config.json`)
- Swap Claude API and OpenRouter card positions (OpenRouter beside OpenAI, Claude API at bottom)

### Infrastructure
- Bind HTTP server to `0.0.0.0` (was `127.0.0.1`) so PC browsers on same network can POST data
- Improve OpenRouter DOM parsing with MutationObserver retry and timeout fallback

## v1.0 (2026-05-15)

### Initial Release
- Full Android dashboard with 5 AI service monitors
- Home Assistant-style card layout (configurable sections, columns, spans)
- Flip-clock card ported from desktop version
- WebView + JS injection data pipeline
- HTTP server (port 7890) for Tampermonkey data
- Dark theme (Linear/Raycast style)
- Full-screen WebView login with cookie persistence
- Google SSO block detection with warning banner
- Foreground Service to keep HTTP server alive
- Desktop Chrome UA to avoid mobile redirects

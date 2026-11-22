# Arena (`com.arena.aid`)

Native Android client for **arena.ai** (Chat Mode, Code Mode, and Agent Mode), built in Kotlin and Jetpack Compose with the **Material 3 Expressive (`m3x`)** design system and **Poppins** typography.

## Highlights

- **Performance-Optimized Native Compose UI**: Engineered for ultra-low memory footprint and smooth 60fps performance on **4GB RAM / Android Go** devices (including **itel A60s** / Unisoc SC9863A, `minSdk = 24`).
- **Real `arena.ai` Model Selector & Live Scraper**:
  - Uses the exact user-selectable models and UUIDv7 IDs from `arena.ai/?mode=direct` (`Max` router, `gemini-4-argon-high`, `claude-sonnet-5-5-high`, `gpt-6-luna-max`, `deepseek-v4.1-flash-max`, `mimo-v2.6-pro`, `qwen3.8-27b`, `amazon-web-search-agent`, `grok-4.5-search`, `gemini-3.1-pro-grounding`, and more).
  - Live-scrapes `/nextjs-api/model-catalog` and `window.__next_f` `initialModels` on startup and on manual refresh.
- **In-App Cookie Auto-Scraper (`ArenaSessionBridge`)**:
  - Zero DevTools required on mobile: automatically executes `grecaptcha.enterprise` (`sign_up`) and `/nextjs-api/sign-up` inside a low-RAM session bridge and extracts `HttpOnly` `arena-auth-prod-v1` cookies via Android `CookieManager`.
  - Includes 1-tap **Auto-extract cookie** and **Sign in & extract** (with Google OAuth and instant scraper) in Connection Settings and Auth Screen.
- **All 3 Core Modes**:
  - **Chat Mode (`chat`)**: Direct streaming with live reasoning blocks (`ag:`) and text deltas (`a0:`).
  - **Code Mode (`webdev`)**: Automatic multi-file code artifact extraction and copy/preview sheet.
  - **Agent Mode (`search`)**: Live search grounding (`ah:` citations + Wikipedia research step timeline).
- **Single Signed Release APK**:
  - Every push to `main` builds and signs a single universal release APK using `arena-release.keystore` (SHA-1: `A1:2E:D2:4C:90:3B:2C:BB:96:9C:7A:0A:E1:1E:0C:12:A9:CF:85:6E`) and publishes it to GitHub Releases.

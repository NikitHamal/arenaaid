# Arena (`com.arena.aid`) — Native Android M3 Expressive Status

## Overview
- **App Name**: Arena
- **Package / Namespace**: `com.arena.aid`
- **Repository**: `https://github.com/NikitHamal/arenaaid` (`main`)
- **Releases**: Single universal signed release APK (`Arena-release.apk`) built and published automatically on every push to `main` via `.github/workflows/build-release.yml` using the repository's single persistent keystore (`arena-release.keystore`, alias `arena`).
- **Design System**: Material 3 Expressive (`m3x`) — NEBians Brand Blue (`#0058DF`), Poppins font family (`poppins_regular`, `poppins_medium`, `poppins_semibold`, `poppins_bold`), neutral grey light surface ramp, graphite dark surface ramp, zero shadows (`elevation = 0.dp`), zero emojis, zero gradients, finite rounded corners (`12dp`–`44dp`), expressive `MaterialShapes` avatars (`Cookie9Sided`, `Sunny`, `Flower`, `Clover4Leaf`, `SoftBurst`, `Oval`, `Square`), and morphing `LoadingIndicator` (`ArenaLoader`).
- **Target Hardware Optimization**: Specifically engineered for **itel A60s (4GB RAM / Android Go / Unisoc SC9863A)** and all Android `minSdk = 23`+ devices.

## Milestone Checklist
- [x] **M1: Repository, Keystore & CI Pipeline**
  - Created `https://github.com/NikitHamal/arenaaid` (`main`).
  - Generated single persistent `arena-release.keystore` (`PKCS#12` with `PBE-SHA1-3DES` / `SHA1` for universal JDK 17 and `apksigner` compatibility).
  - Configured Gradle `9.9.0`, AGP `9.5.1`, Kotlin & Compose `2.3.20`, KSP `2.3.9`, Hilt `2.60.1`, Room `2.8.5`, Material 3 Expressive `1.5.0-alpha10`, and `compose_stability.conf`.
  - Built `tools/rsi/rsi.py` (`pickup`, `verify`, `checkpoint`, `ci`) enforcing M3X shadow/gradient/emoji bans, `ArenaModalSheet` usage, and `boundedWidth()` / `arenaHorizontalScroll()` crash guards.
- [x] **M2: M3 Expressive (`m3x`) Theme & Primitives**
  - `Palette.kt`: Exact M3X light and dark token ramps.
  - `Type.kt`: Poppins typography across all 15 Material 3 type roles + monospace `ArenaCodeTextStyle`.
  - `ArenaSpring.kt`: `MotionScheme.expressive()` spatial and effects springs with automatic snap fallback when system animations are disabled or Low-RAM Reduced Motion is toggled.
  - `Theme.kt`: `ArenaTheme` with `MaterialExpressiveTheme` and finite rounded `ArenaIndication` press feedback.
  - `AvatarShapes.kt`, `ArenaPrimitives.kt`, `ArenaSheetAndDialog.kt`, `ArenaMarkdownAndCode.kt`: Frameless `MaterialShapes` avatars, unfilled `ArenaRoleBadge`, tonal-filled `ArenaStatusChip` & `ArenaFilterChip`, zero-shadow `ArenaCard`, `ArenaModalSheet`, `ArenaDialog`, `ArenaToastHost`, collapsible `ArenaReasoningDrawer`, and line-numbered syntax-highlighted `ArenaCodeBlockCard`.
- [x] **M3: Data Layer & Live Arena.ai Engine**
  - `ArenaDatabase.kt`: Room database in `WRITE_AHEAD_LOGGING` (WAL) mode with `chat_sessions`, `messages`, `code_artifacts`, and `agent_steps` tables.
  - `ArenaPreferences.kt`: Encrypted SharedPreferences (`AES256_SIV` / `AES256_GCM`) + real-time `ActivityManager.MemoryInfo` RAM telemetry (`inspectDeviceRam()`).
  - `ArenaNetworkEngine.kt`: Supports `https://arena.ai/nextjs-api/stream/create-evaluation`, `/nextjs-api/evaluation/{id}/vote`, custom OpenAI/Gemini/OpenRouter `/v1/chat/completions`, live Wikipedia REST API search for Agent Mode's `web_search` tool, and frame-coalesced token streaming (`28ms` batching on 4GB RAM devices).
- [x] **M4: Native End-to-End Screens (Zero WebView)**
  - `AuthScreen.kt`: Google Sign-In via Android Credential Manager (`GetGoogleIdOption`) + Google account picker sheet + Custom Tab OAuth fallback, Email/Password Sign-In & Sign-Up, Magic Link, and `arena-auth-prod-v1.0` cookie / API key setup.
  - `ChatAndBattleScreen.kt`: Direct Chat, Side-by-Side comparison, and Blind Battle Mode with anonymous `Assistant A` vs `Assistant B` streaming, voting (`A is better`, `Tie`, `B is better`), and model identity + Elo reveal.
  - `CodeArenaScreen.kt`: Multi-file code artifact tabs, line-numbered syntax viewer, Unified Diff comparison between Model A and Model B, and one-tap refactor actions (`Optimize 4GB RAM`, `Fix Bugs & Edge Cases`, `Generate Unit Tests`).
  - `AgentArenaScreen.kt`: Autonomous multi-step tool execution timeline (`web_search`, `bash_sandbox`, `file_write`, `deep_research`) with M3X status chips (`Autopilot`, `Delivered`, `Solved`, `Answer`) and deliverable report.
  - `LeaderboardAndSettingsScreen.kt`: Searchable Elo leaderboards across 6 Arena categories (`Text`, `Code`, `Agent`, `Hard prompts`, `Math`, `Vision`), Session History sheet with pin/search/delete, and 4GB RAM / Android Go Turbo Engine settings.

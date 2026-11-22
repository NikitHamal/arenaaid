# Arena (`com.arena.aid`) — Native Android M3 Expressive Status

## Overview
- **App Name**: Arena
- **Package**: `com.arena.aid`
- **Repository**: `https://github.com/NikitHamal/arenaaid` (`main`)
- **Design System**: Material 3 Expressive (`m3x`) — NEBians Brand Blue (`#0058DF`), Poppins font family, zero shadows (`0.dp`), zero emojis, zero gradients, finite rounded corners (`12dp`–`44dp`), expressive `MaterialShapes` avatars & morphing `LoadingIndicator`.
- **Target Hardware Optimization**: Specifically engineered for **itel A60s (4GB RAM / Android Go / Unisoc SC9863A)** and all Android `minSdk = 23`+ devices with single universal signed release APK (`arena-release.keystore`).

## Milestone Checklist
- [x] M1: Repository bootstrap, single `arena-release.keystore`, Gradle 9.9 / AGP 9.5.1 / Kotlin 2.3.20 / Compose M3 Expressive 1.5.0-alpha10, Poppins fonts, RSI v2 guard pipeline, and GitHub Actions release workflow.
- [ ] M2: M3 Expressive (`m3x`) Theme & Primitives (`Palette.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`, `ArenaSpring.kt`, `ArenaFloat.kt`, `ArenaIndication.kt`, `SafeScroll.kt`, `AvatarShapes.kt`, `ArenaDesign.kt`, `ArenaSheet.kt`, `ArenaDialog.kt`, `ArenaToast.kt`, `ArenaLoader.kt`).
- [ ] M3: Data Layer — Room Database (`ArenaDatabase`), Encrypted Auth Store (Google Sign-In via Credential Manager + Email/Password + Magic Link + Session Cookie `arena-auth-prod-v1` + Custom API Key), and Live `arena.ai` Streaming Client (`nextjs-api/stream/create-evaluation`, `post-to-evaluation`, battle vote, leaderboard sync, and intelligent low-RAM frame coalescer).
- [ ] M4: Native Screens — Auth Screen (Google + Email + Cookie), Chat Mode (Battle, Side-by-Side, Direct Chat with reasoning trace & Markdown/Code rendering), Code Mode (Native Code Arena IDE, multi-file artifact tabs, syntax viewer, diff inspector), Agent Mode (Autonomous multi-step tool execution timeline, live web search, sandbox terminal, workspace files), Leaderboard & Model Explorer, History & Settings (with 4GB RAM / Android Go Turbo Engine telemetry).
- [ ] M5: Full GitHub Actions CI green build & signed GitHub Release APK verification.

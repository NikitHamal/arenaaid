# Arena (`com.arena.aid`)

Native Android client for **Arena.ai** (Chat Mode, Code Mode, Agent Mode, Blind Battle, Side-by-Side, and Live Leaderboards), built 100% in Kotlin and Jetpack Compose with the **Material 3 Expressive (`m3x`)** design system and **Poppins** typography.

## Highlights

- **Pure Native Kotlin & Compose (Zero WebView)**: Engineered from the ground up for ultra-low memory footprint and smooth 60fps performance on **4GB RAM / Android Go** devices (including **itel A60s** / Unisoc SC9863A, `minSdk = 23`).
- **All Arena.ai Modes**:
  - **Chat Mode**: Direct Chat with any frontier or open-weight model, Blind Battle Mode (2 anonymous models + voting + Elo reveal), and Side-by-Side comparison.
  - **Code Mode (Code Arena)**: Native mobile IDE with automatic multi-file artifact extraction, line-numbered code viewer, unified diff comparison between models, and one-tap coding actions.
  - **Agent Mode (Agent Arena)**: Autonomous multi-step task execution with live tool timeline (`web_search`, `bash_sandbox`, `file_write`, `deep_research`, and interactive follow-up prompts).
  - **Arena Leaderboards**: Live and offline-cached Elo rankings across Text, Code, Agent, Hard Prompts, Math, and Vision categories.
- **Seamless Authentication**:
  - Google Sign-In via Android Credential Manager (`androidx.credentials` + `googleid`) and OAuth PKCE flow.
  - Email & Password Sign-In / Sign-Up and Magic Link support.
  - Direct `arena.ai` session cookie (`arena-auth-prod-v1.0` / `v1.1`) and custom API endpoint/key integration.
- **Material 3 Expressive (`m3x`) Design System**:
  - Brand Blue (`#0058DF`), neutral light surface ramp, graphite dark surface ramp.
  - Zero shadows (`elevation = 0.dp`), 1dp `outlineVariant` hairlines, finite corner radii (`12dp`–`44dp`), expressive `MaterialShapes` avatars, and spring physics (`MotionScheme.expressive()`).
- **Single Signed Release APK**:
  - Every CI run signs the release APK with the single repository keystore (`arena-release.keystore`) and publishes directly to GitHub Releases.

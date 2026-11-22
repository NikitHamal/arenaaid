# Arena (`com.arena.aid`) - Status

Native Kotlin + Compose (Material 3 Expressive, Poppins) client for arena.ai. No WebView.
Repo: https://github.com/NikitHamal/arenaaid (`main`). CI builds one signed release APK per push
(`.github/workflows/build-release.yml`, keystore `arena-release.keystore`, alias `arena`).
Never build Gradle locally; use `python3 tools/rsi/rsi.py pickup|verify|ci`.

## Scope (v2 cleanup)
- Modes: Chat, Code, Agent. Single conversation thread with follow-ups.
- Removed: leaderboard/ranks, battle, side-by-side, fake Elo, fake agent steps, fake synthesized replies.
- Session history: left drawer (search, Today/Yesterday/Previous 7 days/Older, pin, rename, delete).
- Settings screen: account, connection, theme, low memory, reduce motion, custom instructions, clear history.

## Architecture
- `data/remote/ArenaNetworkEngine.kt`: real SSE streaming. OpenAI-compatible endpoint (key set) or
  arena.ai `create-evaluation` (cookie set). No backend configured -> explicit "Not connected" error.
- `data/repository/ArenaRepository.kt`: Room persistence, multi-turn history, Agent web_search step
  (Wikipedia API), code artifact extraction in Code mode.
- `ui/ArenaViewModel.kt`: single state holder. UI in `ui/screens/{auth,chat,history,settings}`.

## Known limits
- arena.ai protects `create-evaluation` with reCAPTCHA + Cloudflare. A native client can send the session
  cookie but cannot mint a browser reCAPTCHA token, so requests may be refused (403). The API-key path
  (OpenRouter or any OpenAI-compatible URL) is the reliable route.
- arena.ai has no `/login` page (old browser OAuth button opened a 404). Connection now opens
  `https://arena.ai/?mode=direct` in a Custom Tab and takes the pasted session cookie.
- Google Sign-In needs the Android OAuth client (package + release SHA-1) registered in Google Cloud.

## Next
- Verify CI is green, install the release APK, test on itel A60s.

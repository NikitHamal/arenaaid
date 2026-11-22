# Arena (`com.arena.aid`) - Status

Native Kotlin + Compose (Material 3 Expressive, Poppins) client for `arena.ai`.
Repo: `https://github.com/NikitHamal/arenaaid` (`main`).
CI builds one signed release APK per push (`.github/workflows/build-release.yml`, keystore `arena-release.keystore`, alias `arena`, SHA-1 `A1:2E:D2:4C:90:3B:2C:BB:96:9C:7A:0A:E1:1E:0C:12:A9:CF:85:6E`).
Never build Gradle locally; use `python3 tools/rsi/rsi.py pickup|verify|checkpoint|ci`.

## Latest Improvements (v1.2.0 / `versionCode = 3`) - fix for HTTP 400/403 errors
Root causes found (verified against public arena.ai reverse-engineering sources):
- `create-evaluation` needs a valid `recaptchaV3Token` (reCAPTCHA Enterprise, action `chat_submit`). The old bridge used the wrong site key and, when the WebView path errored, **silently fell back to a bare OkHttp POST** (no Cloudflare clearance, no token) -> the generic "400/403". The real error body was hidden.
- A synthetic `cx` field (unverifiable port) was sent; removed from the fallback body.
- Saved cookie overwrote the fresher live WebView cookie on every send.

Fixes:
- `ArenaPageScripts.kt` (new): JS run inside the real arena.ai page. Primary path drives arena.ai's **own composer** (fill textarea, click send); a `fetch` hook rewrites only `modelAId`/`modality` of the page-built request (so recaptcha, `cx`, userId, ids are the site's own) and tees the SSE stream to native. Fallback: direct fetch with a fresh token using the site key **discovered from the page's `recaptcha/enterprise.js?render=` script**.
- `ArenaSessionBridge.kt` rewritten: single mutex, composer readiness probe (Cloudflare detection), page reloaded fresh after each chat (warm for next), errors carry status + response body, 400/401/403/429 messages explain next step, no blind OkHttp fallback for arena path.
- Cookie auto-extract waits for the page's own anonymous sign-up, runs sign-up fallback only if needed, never replaces an existing auth cookie. Pasted cookies are force-applied to the session on Save.
- Model scrape returns `{catalog, rsc}` and uses whichever parser finds more models (page `initialModels` is the source of truth).
- API-key (OpenAI-compatible) errors now show the server's message instead of "Request failed (400)".
- Page URL is now `https://arena.ai/text/direct`.

Cannot be exercised from CI/sandbox (Cloudflare blocks it): needs on-device verification. If arena.ai changes composer markup, the direct-fetch fallback engages after ~18s.

## Previous (v1.1.0)
- **Real `arena.ai` Model Selector Catalog & Dual Live Scraper (`ArenaModels.kt`, `ArenaNetworkEngine.kt`, `ArenaSessionBridge.kt`, `ChatSheets.kt`)**:
  - Replaced synthetic/OpenRouter model list with the exact user-selectable models and UUIDv7 IDs from `https://arena.ai/?mode=direct`:
    - **Chat (`chat` modality)**: `Max` (`019b24bb-5caf-71c3-b854-37d0c7086f21`), `gemini-4-argon-high` (`01a141d8-c8f9-710a-bdf4-983605ae330c`), `gemini-3.8-flash-high`, `mimo-v2.6-pro`, `anonymous-0825`, `mimo-v2.6-flash`, `gpt-6-luna-max`, `qwen3.8-27b`, `claude-haiku-5.5-high`, `deepseek-v4.1-flash-high`, `deepseek-v4.1-flash-max`, `muse-ember-max`, `kimi-k3`, `claude-sonnet-5-5-high`, `gpt-6.1-terra-low`, `inkling-medium`, `muse-spark-1.3-xhigh`, `grok-4.6`.
    - **Code (`webdev` modality)**: `Max`, `claude-sonnet-5-5-high`, `gemini-4-argon-high`, `paisley`, `deepseek-v4.1-flash-max`, `mimo-v2.6-flash`, `mimo-v2.6-pro`, `muse-spark-1.3-xhigh`, `anonymous-0825`, `muse-ember-max`, `claude-haiku-5.5-high`, `qwen3.8-27b`, `gemini-3.8-flash-high`, `gpt-6-luna-max`, `deepseek-v4.1-flash-high`, `inkling-medium`, `ernie-5.5-flash-preview`.
    - **Agent (`search` + `chat` modality)**: `Max`, `amazon-web-search-agent`, `grok-4.5-search`, `gemini-3.1-pro-grounding`, `claude-sonnet-5-search`, `gpt-6-luna-search-max`, plus top reasoning/coding models.
  - Added `ArenaSessionBridge.scrapeModelsPayload()` + `ArenaNetworkEngine.parseModelCatalogJson()` and `parseInitialModelsFromRscText()` so live models are scraped directly from `/nextjs-api/model-catalog` and `window.__next_f` inside the Chromium session bridge (bypassing Cloudflare 403 challenges).
- **In-App Automatic Cookie Scraper & Session Bridge (`ArenaSessionBridge.kt`, `ArenaClientInteraction.kt`, `ConnectionSheet.kt`, `AuthScreen.kt`)**:
  - Built `ArenaSessionBridge` so mobile users without DevTools never have to manually inspect or copy `HttpOnly` cookies (`arena-auth-prod-v1`).
  - Automatically extracts `arena-auth-prod-v1` via Android's `CookieManager.getInstance().getCookie("https://arena.ai")` after running `grecaptcha.enterprise.execute("6LeTGMcsAAAAALuIlkVwIxaAuZA8VledA6d3Nnb0", {action: "sign_up"})` -> `POST /nextjs-api/sign-up` (including `initialUserId` extraction and HTTP `409` `provisionalIdRetry` handling).
  - Sanitized WebView User-Agent (`sanitizeWebViewUa`) to strip `; wv` while preserving the device's real Chromium version so Cloudflare client-hint validation and Google OAuth both pass.
  - Added 1-tap **Auto-extract cookie** and **Sign in & extract** interactive scraper sheet in `ConnectionSheet` and `AuthScreen`.
  - Fixed `ArenaNetworkEngine` constructor clash so Hilt DI and unit tests compile cleanly.

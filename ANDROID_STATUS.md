# Arena (`com.arena.aid`) - Status

Native Kotlin + Compose (Material 3 Expressive, Poppins) client for `arena.ai`.
Repo: `https://github.com/NikitHamal/arenaaid` (`main`).
CI builds one signed release APK per push (`.github/workflows/build-release.yml`, keystore `arena-release.keystore`, alias `arena`, SHA-1 `A1:2E:D2:4C:90:3B:2C:BB:96:9C:7A:0A:E1:1E:0C:12:A9:CF:85:6E`).
Never build Gradle locally; use `python3 tools/rsi/rsi.py pickup|verify|checkpoint|ci`.

## Latest Improvements (v1.1.0 / `versionCode = 2`)
- **Real `arena.ai` Model Selector Catalog & Live Scraper (`ArenaModels.kt`, `ArenaNetworkEngine.kt`, `ChatSheets.kt`)**:
  - Replaced synthetic/OpenRouter model list with the exact user-selectable models and UUIDv7 IDs from `https://arena.ai/?mode=direct` (`initialModels`):
    - **Chat (`chat` modality)**: `Max` (`019b24bb-5caf-71c3-b854-37d0c7086f21`), `gemini-4-argon-high` (`01a141d8-c8f9-710a-bdf4-983605ae330c`), `gemini-3.8-flash-high`, `mimo-v2.6-pro`, `anonymous-0825`, `mimo-v2.6-flash`, `gpt-6-luna-max`, `qwen3.8-27b`, `claude-haiku-5.5-high`, `deepseek-v4.1-flash-high`, `deepseek-v4.1-flash-max`, `muse-ember-max`, `kimi-k3`, `claude-sonnet-5-5-high`, `gpt-6.1-terra-low`, `inkling-medium`, `muse-spark-1.3-xhigh`, `grok-4.6`.
    - **Code (`webdev` modality)**: `Max`, `claude-sonnet-5-5-high`, `gemini-4-argon-high`, `paisley`, `deepseek-v4.1-flash-max`, `mimo-v2.6-flash`, `mimo-v2.6-pro`, `muse-spark-1.3-xhigh`, `anonymous-0825`, `muse-ember-max`, `claude-haiku-5.5-high`, `qwen3.8-27b`, `gemini-3.8-flash-high`, `gpt-6-luna-max`, `deepseek-v4.1-flash-high`, `inkling-medium`, `ernie-5.5-flash-preview`.
    - **Agent (`search` + `chat` modality)**: `Max`, `amazon-web-search-agent`, `grok-4.5-search`, `gemini-3.1-pro-grounding`, `claude-sonnet-5-search`, `gpt-6-luna-search-max`, plus top reasoning/coding models.
  - Added `ArenaNetworkEngine.scrapeLiveModels()` to fetch `https://arena.ai/?mode=direct` on launch and on manual refresh, parse Next.js RSC `self.__next_f.push` chunks, and update `ArenaCatalog` live.
- **In-App Automatic Cookie Scraper & Session Bridge (`ArenaSessionBridge.kt`, `ArenaClientInteraction.kt`, `ConnectionSheet.kt`, `AuthScreen.kt`)**:
  - Built `ArenaSessionBridge` so mobile users without DevTools never have to manually inspect or copy `HttpOnly` cookies (`arena-auth-prod-v1`).
  - Automatically extracts `arena-auth-prod-v1` via Android's `CookieManager.getInstance().getCookie("https://arena.ai")` after running `grecaptcha.enterprise.execute("6LeTGMcsAAAAALuIlkVwIxaAuZA8VledA6d3Nnb0", {action: "sign_up"})` -> `POST /nextjs-api/sign-up` inside a low-RAM asset-blocking headless bridge.
  - Added 1-tap **Auto-extract cookie** and **Sign in & extract** interactive scraper sheet in `ConnectionSheet` and `AuthScreen`.
  - Updated `/nextjs-api/stream/create-evaluation` request schema (`userMessage`, `modelAId` UUIDv7, `modality`, `recaptchaV3Token`, and 20-char Base64URL `"cx"` `ArenaClientInteraction` token) and removed the blocking "Connect first" gate on Send.

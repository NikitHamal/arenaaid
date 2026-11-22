package com.arena.aid.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hidden Chromium session on arena.ai. All traffic that needs Cloudflare clearance, reCAPTCHA
 * Enterprise or the auth cookie runs inside the genuine page, so the requests are the site's own.
 */
@Singleton
class ArenaSessionBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ARENA_URL = "https://arena.ai/text/direct"
        const val GOOGLE_SIGN_IN_URL = "https://arena.ai/nextjs-api/sign-in/google?shouldLinkHistory=true&returnTo=%2Ftext%2Fdirect"
        const val RECAPTCHA_SITE_KEY = ArenaPageScripts.V3_KEY
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; itel A60s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val DONE = "[[DONE]]"
        private const val ERR = "[[ERR]]"

        fun sanitizeWebViewUa(raw: String?): String {
            if (raw.isNullOrBlank()) return USER_AGENT
            return raw
                .replace("; wv)", ")")
                .replace("; wv;", ";")
                .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
        }

        fun signUpJs(fallbackProId: String): String = ArenaPageScripts.signUp(fallbackProId)
    }

    private val mutex = Mutex()
    private var webView: WebView? = null
    @Volatile private var pageReady = false
    @Volatile private var pageFresh = false
    @Volatile private var activeChannel: Channel<String>? = null
    @Volatile private var modelsDeferred: CompletableDeferred<String>? = null

    fun readCookie(): String = runCatching {
        CookieManager.getInstance().getCookie("https://arena.ai").orEmpty().trim()
    }.getOrDefault("")

    fun hasAuthCookie(cookie: String = readCookie()): Boolean = cookie.contains("arena-auth")

    /** Restores a saved/pasted cookie into the session; never clobbers a live auth cookie unless forced. */
    fun syncCookieToSystem(cookieHeader: String, force: Boolean = false) {
        if (cookieHeader.isBlank() || (!force && hasAuthCookie())) return
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cookieHeader.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { pair ->
                cm.setCookie("https://arena.ai", "$pair; Domain=arena.ai; Path=/; Secure")
            }
            cm.flush()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureWebView(): WebView = withContext(Dispatchers.Main) {
        webView?.let { return@withContext it }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.blockNetworkImage = true
            settings.userAgentString = sanitizeWebViewUa(settings.userAgentString)
            cm.setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(BridgeJsInterface(), "ArenaNative")
            val dm = context.resources.displayMetrics
            val w = View.MeasureSpec.makeMeasureSpec(dm.widthPixels.coerceAtLeast(360), View.MeasureSpec.EXACTLY)
            val h = View.MeasureSpec.makeMeasureSpec(dm.heightPixels.coerceAtLeast(640), View.MeasureSpec.EXACTLY)
            measure(w, h)
            layout(0, 0, measuredWidth, measuredHeight)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val u = request?.url?.toString()?.lowercase().orEmpty()
                    if (u.endsWith(".woff2") || u.endsWith(".ttf") || u.endsWith(".mp4") ||
                        u.contains("posthog") || u.contains("datadog") || u.contains("sentry")
                    ) {
                        return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    pageReady = false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                }
            }
            pageFresh = true
            loadUrl(ARENA_URL)
        }
        webView = wv
        wv
    }

    private suspend fun loadFresh() {
        val wv = webView ?: return
        withContext(Dispatchers.Main) {
            pageReady = false
            pageFresh = true
            wv.loadUrl(ARENA_URL)
        }
    }

    private suspend fun eval(js: String): String = withContext(Dispatchers.Main) {
        val def = CompletableDeferred<String>()
        val wv = webView
        if (wv == null) def.complete("null") else wv.evaluateJavascript(js) { def.complete(it ?: "null") }
        def.await()
    }

    /** Waits until the arena.ai page is loaded with a composer. Returns "ok", "cf" or "timeout". */
    private suspend fun awaitComposer(timeoutMs: Long, needComposer: Boolean = true): String {
        var last = "timeout"
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (pageReady) {
                    val s = eval(ArenaPageScripts.STATE_JS)
                    last = if (s.contains("cf=1")) "cf" else "timeout"
                    if (s.contains("cf=0") && (!needComposer || s.contains("ta=1"))) { last = "ok"; return@withTimeoutOrNull }
                }
                delay(400L)
            }
        }
        return last
    }

    suspend fun autoExtractCookie(forceSignUp: Boolean = false): String = mutex.withLock {
        val existing = readCookie()
        if (hasAuthCookie(existing)) return@withLock existing
        runCatching { ensureWebView() }.getOrNull() ?: return@withLock existing
        withTimeoutOrNull(40_000L) {
            awaitComposer(25_000L, needComposer = false)
            var found = ""
            for (tries in 0 until 70) {
                val c = readCookie()
                if (hasAuthCookie(c)) {
                    runCatching { CookieManager.getInstance().flush() }
                    found = c
                    break
                }
                if (tries == 10 || (forceSignUp && tries == 24)) {
                    withContext(Dispatchers.Main) {
                        webView?.evaluateJavascript(ArenaPageScripts.signUp(ArenaClientInteraction.uuidV7()), null)
                    }
                }
                delay(500L)
            }
            found
        } ?: readCookie()
    }

    /** Returns JSON `{catalog, rsc}` scraped from the live page, or "" when unavailable. */
    suspend fun scrapeModelsPayload(): String = mutex.withLock {
        withTimeoutOrNull(30_000L) {
            runCatching { ensureWebView() }.getOrNull() ?: return@withTimeoutOrNull ""
            if (awaitComposer(20_000L, needComposer = false) != "ok") return@withTimeoutOrNull ""
            val def = CompletableDeferred<String>()
            modelsDeferred = def
            withContext(Dispatchers.Main) { webView?.evaluateJavascript(ArenaPageScripts.MODELS_JS, null) }
            def.await()
        }.orEmpty()
    }

    /**
     * Sends a prompt via the live arena.ai page. [onLine] receives raw stream lines.
     * Returns null on success or a human readable error (with the server response body).
     */
    suspend fun runChat(
        modelId: String,
        modality: String,
        prompt: String,
        fallbackBody: String,
        onLine: suspend (String) -> Unit
    ): String? = mutex.withLock {
        runCatching { ensureWebView() }.getOrNull() ?: return@withLock "WebView is unavailable on this device."
        if (!pageFresh) loadFresh()
        when (awaitComposer(35_000L)) {
            "ok" -> Unit
            "cf" -> return@withLock "Cloudflare is verifying this device (403). Open Settings > Connection > Sign in & extract, wait for arena.ai to load once, then retry."
            else -> return@withLock "arena.ai did not finish loading. Check your connection and retry."
        }
        val ch = Channel<String>(Channel.UNLIMITED)
        activeChannel = ch
        var err: String? = null
        try {
            pageFresh = false
            withContext(Dispatchers.Main) {
                webView?.evaluateJavascript(ArenaPageScripts.run(modelId, modality, prompt, fallbackBody), null)
            }
            while (true) {
                val msg = withTimeoutOrNull(100_000L) { ch.receiveCatching().getOrNull() }
                if (msg == null) { err = "Timed out waiting for arena.ai to answer."; break }
                if (msg == DONE) break
                if (msg.startsWith(ERR)) { err = explain(msg.removePrefix(ERR)); break }
                onLine(msg)
            }
        } finally {
            activeChannel = null
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { webView?.evaluateJavascript("window.__arenaAbort && window.__arenaAbort()", null) }
                loadFresh()
            }
        }
        err
    }

    private fun explain(raw: String): String {
        val code = raw.substringBefore(':', "").trim().toIntOrNull()
        val body = raw.substringAfter(':', raw).trim().take(300)
        return when {
            code == 403 && body.contains("recaptcha", true) ->
                "arena.ai rejected the security check (403: $body). Open Settings > Connection > Sign in & extract, complete any verification shown, then retry."
            code == 400 -> "arena.ai rejected the request (400: ${body.ifBlank { "no details" }})."
            code == 401 -> "Session expired (401). Reconnect in Settings > Connection."
            code == 429 -> "Rate limited by arena.ai (429). Try again shortly."
            code != null -> "arena.ai returned $code: ${body.ifBlank { "no details" }}"
            else -> raw.take(300)
        }
    }

    private inner class BridgeJsInterface {
        @JavascriptInterface
        fun onLine(line: String) { activeChannel?.trySend(line) }

        @JavascriptInterface
        fun onDone() { activeChannel?.trySend(DONE); activeChannel?.close() }

        @JavascriptInterface
        fun onError(err: String) { activeChannel?.trySend(ERR + err); activeChannel?.close() }

        @JavascriptInterface
        fun onModels(payload: String) { modelsDeferred?.complete(payload) }
    }
}

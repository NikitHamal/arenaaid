package com.arena.aid.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArenaSessionBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ARENA_URL = "https://arena.ai/?mode=direct"
        const val RECAPTCHA_SITE_KEY = "6LeTGMcsAAAAALuIlkVwIxaAuZA8VledA6d3Nnb0"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; itel A60s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    private val mutex = Mutex()
    private var webView: WebView? = null
    private var pageReady = false
    private var activeChannel: Channel<String>? = null

    fun readCookie(): String = runCatching {
        CookieManager.getInstance().getCookie("https://arena.ai").orEmpty().trim()
    }.getOrDefault("")

    fun hasAuthCookie(cookie: String = readCookie()): Boolean =
        cookie.contains("arena-auth-prod-v1") || cookie.contains("arena-auth")

    fun syncCookieToSystem(cookieHeader: String) {
        if (cookieHeader.isBlank()) return
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
            settings.databaseEnabled = true
            settings.blockNetworkImage = true
            settings.userAgentString = USER_AGENT
            cm.setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(BridgeJsInterface(), "ArenaNative")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val u = request?.url?.toString()?.lowercase().orEmpty()
                    if (u.endsWith(".woff2") || u.endsWith(".ttf") || u.endsWith(".png") ||
                        u.endsWith(".jpg") || u.endsWith(".webp") || u.endsWith(".mp4") ||
                        u.contains("posthog") || u.contains("datadog") || u.contains("cloudflareinsights")
                    ) {
                        return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                }
            }
            loadUrl(ARENA_URL)
        }
        webView = wv
        wv
    }

    suspend fun autoExtractCookie(forceSignUp: Boolean = false): String = mutex.withLock {
        val existing = readCookie()
        if (!forceSignUp && hasAuthCookie(existing)) return existing
        val wv = runCatching { ensureWebView() }.getOrNull() ?: return existing
        withTimeoutOrNull(22_000L) {
            while (!pageReady) delay(250L)
            val proId = ArenaClientInteraction.uuidV7()
            withContext(Dispatchers.Main) {
                wv.evaluateJavascript(
                    """
                    (async function(){
                      try {
                        if (window.grecaptcha && window.grecaptcha.enterprise) {
                          window.grecaptcha.enterprise.ready(async function(){
                            try {
                              var tok = await window.grecaptcha.enterprise.execute('$RECAPTCHA_SITE_KEY', {action: 'sign_up'});
                              await fetch('/nextjs-api/sign-up', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/json'},
                                credentials: 'include',
                                body: JSON.stringify({recaptchaToken: tok || '', provisionalUserId: '$proId'})
                              });
                            } catch(e) {}
                          });
                        }
                      } catch(e) {}
                    })();
                    """.trimIndent(),
                    null
                )
            }
            repeat(40) {
                val c = readCookie()
                if (hasAuthCookie(c)) {
                    runCatching { CookieManager.getInstance().flush() }
                    return@withTimeoutOrNull c
                }
                delay(400L)
            }
            readCookie()
        } ?: readCookie()
    }

    suspend fun streamViaWebView(endpointPath: String, payloadJson: String, onLine: suspend (String) -> Unit): String? {
        val wv = runCatching { ensureWebView() }.getOrNull() ?: return "WebView unavailable"
        val ch = Channel<String>(Channel.UNLIMITED)
        activeChannel = ch
        val proId = ArenaClientInteraction.uuidV7()
        val escapedPayload = payloadJson.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        withContext(Dispatchers.Main) {
            if (!pageReady) {
                var waited = 0
                while (!pageReady && waited < 8000) { delay(200L); waited += 200 }
            }
            wv.evaluateJavascript(
                """
                (async function(){
                  try {
                    var bodyObj = JSON.parse('$escapedPayload');
                    if (window.grecaptcha && window.grecaptcha.enterprise) {
                      await new Promise(r => window.grecaptcha.enterprise.ready(r));
                      if (!document.cookie.includes('arena-auth')) {
                        try {
                          var suTok = await window.grecaptcha.enterprise.execute('$RECAPTCHA_SITE_KEY', {action: 'sign_up'});
                          await fetch('/nextjs-api/sign-up', {
                            method: 'POST',
                            headers: {'Content-Type': 'application/json'},
                            credentials: 'include',
                            body: JSON.stringify({recaptchaToken: suTok || '', provisionalUserId: '$proId'})
                          });
                        } catch(e) {}
                      }
                      try {
                        bodyObj.recaptchaV3Token = await window.grecaptcha.enterprise.execute('$RECAPTCHA_SITE_KEY', {action: 'chat_submit'});
                      } catch(e) {}
                    }
                    var resp = await fetch('$endpointPath', {
                      method: 'POST',
                      headers: {'Content-Type': 'application/json'},
                      credentials: 'include',
                      body: JSON.stringify(bodyObj)
                    });
                    if (!resp.ok) {
                      var errTxt = '';
                      try { errTxt = await resp.text(); } catch(e) {}
                      ArenaNative.onError(resp.status + ': ' + errTxt);
                      return;
                    }
                    var reader = resp.body.getReader();
                    var dec = new TextDecoder('utf-8');
                    var buf = '';
                    while (true) {
                      var part = await reader.read();
                      if (part.done) break;
                      buf += dec.decode(part.value, {stream: true});
                      var lines = buf.split('\n');
                      buf = lines.pop() || '';
                      for (var i = 0; i < lines.length; i++) {
                        if (lines[i]) ArenaNative.onLine(lines[i]);
                      }
                    }
                    if (buf) ArenaNative.onLine(buf);
                    ArenaNative.onDone();
                  } catch(e) {
                    ArenaNative.onError(String(e));
                  }
                })();
                """.trimIndent(),
                null
            )
        }
        for (msg in ch) {
            if (msg == "[[DONE]]") break
            if (msg.startsWith("[[ERR]]")) return msg.removePrefix("[[ERR]]")
            onLine(msg)
        }
        return null
    }

    private inner class BridgeJsInterface {
        @JavascriptInterface
        fun onLine(line: String) { activeChannel?.trySend(line) }

        @JavascriptInterface
        fun onDone() { activeChannel?.trySend("[[DONE]]"); activeChannel?.close() }

        @JavascriptInterface
        fun onError(err: String) { activeChannel?.trySend("[[ERR]]$err"); activeChannel?.close() }
    }
}

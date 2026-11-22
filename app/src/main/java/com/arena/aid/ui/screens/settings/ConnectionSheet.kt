package com.arena.aid.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arena.aid.data.model.ArenaUserProfile
import com.arena.aid.data.remote.ArenaSessionBridge
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaModalSheet
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.ArenaTextField
import kotlinx.coroutines.delay

@Composable
fun ConnectionSheet(
    user: ArenaUserProfile?,
    extracting: Boolean,
    onAutoExtract: ((String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (cookie: String, apiUrl: String, apiKey: String) -> Unit
) {
    val context = LocalContext.current
    var cookie by remember(user?.sessionCookie) {
        val sys = runCatching { CookieManager.getInstance().getCookie("https://arena.ai").orEmpty() }.getOrDefault("")
        mutableStateOf(user?.sessionCookie?.ifBlank { sys }.orEmpty())
    }
    var apiUrl by remember {
        mutableStateOf(user?.customApiUrl?.takeIf { !it.contains("arena.ai") } ?: "https://openrouter.ai/api")
    }
    var apiKey by remember { mutableStateOf(user?.customApiKey.orEmpty()) }
    var showInteractiveScraper by remember { mutableStateOf(false) }
    val hasAuthCookie = cookie.contains("arena-auth")

    if (showInteractiveScraper) {
        InteractiveCookieScraperSheet(
            onExtracted = { extracted ->
                cookie = extracted
                onSave(extracted, apiUrl, apiKey)
                showInteractiveScraper = false
            },
            onDismiss = { showInteractiveScraper = false }
        )
        return
    }

    ArenaModalSheet(onDismiss = onDismiss, title = "Connection") {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("arena.ai session", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                ArenaStatusChip(if (hasAuthCookie) "Cookie active" else "Auto mode")
            }
            ArenaButton(
                text = if (extracting) "Extracting cookie..." else "Auto-extract cookie",
                leadingIcon = Icons.Rounded.AutoFixHigh,
                loading = extracting,
                height = 48.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onAutoExtract { extracted ->
                        cookie = extracted
                        onSave(extracted, apiUrl, apiKey)
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArenaButton(
                    text = "Sign in & extract",
                    leadingIcon = Icons.Rounded.Login,
                    onClick = { showInteractiveScraper = true },
                    modifier = Modifier.weight(1f),
                    tone = ArenaButtonTone.Tonal
                )
                ArenaButton(
                    text = "Paste",
                    onClick = { clipboardText(context)?.let { cookie = it } },
                    modifier = Modifier.weight(1f),
                    tone = ArenaButtonTone.Outlined
                )
            }
            ArenaTextField(
                value = cookie,
                onValueChange = { cookie = it },
                placeholder = "arena-auth-prod-v1=... (auto-extracted)",
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text("OpenAI compatible API", style = MaterialTheme.typography.titleSmall)
            ArenaTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                placeholder = "API base URL",
                modifier = Modifier.fillMaxWidth()
            )
            ArenaTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = "API key (optional)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            ArenaButton(
                text = "Save",
                onClick = { onSave(cookie, apiUrl, apiKey); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InteractiveCookieScraperSheet(
    onExtracted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var detectedCookie by remember { mutableStateOf("") }
    val ready = detectedCookie.contains("arena-auth")

    LaunchedEffect(Unit) {
        while (true) {
            val c = runCatching {
                CookieManager.getInstance().getCookie("https://arena.ai").orEmpty().trim()
            }.getOrDefault("")
            if (c.isNotBlank()) detectedCookie = c
            delay(500L)
        }
    }

    ArenaModalSheet(onDismiss = onDismiss, title = "Extract arena.ai cookie") {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ready) "Session cookie detected" else "Loading arena.ai session...",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                ArenaStatusChip(if (ready) "Ready" else "Waiting")
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(20.dp)),
                factory = { ctx ->
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = ArenaSessionBridge.USER_AGENT
                        cm.setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        loadUrl(ArenaSessionBridge.ARENA_URL)
                    }
                }
            )
            ArenaButton(
                text = if (ready) "Use extracted cookie" else "Capture current cookies",
                enabled = detectedCookie.isNotBlank(),
                height = 52.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching { CookieManager.getInstance().flush() }
                    onExtracted(detectedCookie)
                }
            )
        }
    }
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}

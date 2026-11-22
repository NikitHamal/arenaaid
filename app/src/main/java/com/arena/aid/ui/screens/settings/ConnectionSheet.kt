package com.arena.aid.ui.screens.settings

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arena.aid.data.model.ArenaUserProfile
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaModalSheet
import com.arena.aid.ui.components.ArenaTextField

@Composable
fun ConnectionSheet(
    user: ArenaUserProfile?,
    onDismiss: () -> Unit,
    onSave: (cookie: String, apiUrl: String, apiKey: String) -> Unit
) {
    val context = LocalContext.current
    var cookie by remember { mutableStateOf(user?.sessionCookie.orEmpty()) }
    var apiUrl by remember { mutableStateOf(user?.customApiUrl?.takeIf { !it.contains("arena.ai") } ?: "https://openrouter.ai/api") }
    var apiKey by remember { mutableStateOf(user?.customApiKey.orEmpty()) }

    ArenaModalSheet(onDismiss = onDismiss, title = "Connection") {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("arena.ai session", style = MaterialTheme.typography.titleSmall)
            Text(
                "Sign in on arena.ai, then copy the Cookie header containing arena-auth-prod-v1 and paste it here.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ArenaTextField(
                value = cookie, onValueChange = { cookie = it }, placeholder = "arena-auth-prod-v1=...",
                singleLine = false, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArenaButton("Open arena.ai", { openArena(context) }, Modifier.weight(1f), tone = ArenaButtonTone.Tonal)
                ArenaButton("Paste", { clipboardText(context)?.let { cookie = it } }, Modifier.weight(1f), tone = ArenaButtonTone.Outlined)
            }
            Spacer(Modifier.height(8.dp))
            Text("OpenAI compatible API", style = MaterialTheme.typography.titleSmall)
            ArenaTextField(value = apiUrl, onValueChange = { apiUrl = it }, placeholder = "API base URL", modifier = Modifier.fillMaxWidth())
            ArenaTextField(value = apiKey, onValueChange = { apiKey = it }, placeholder = "API key", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            ArenaButton("Save", { onSave(cookie, apiUrl, apiKey); onDismiss() }, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}

private fun openArena(context: Context) {
    runCatching {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://arena.ai/?mode=direct"))
    }
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}

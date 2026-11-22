package com.arena.aid.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.AgentStepEntity
import com.arena.aid.data.local.MessageEntity
import com.arena.aid.data.repository.ERROR_PREFIX
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaLoader
import com.arena.aid.ui.components.ArenaMarkdownView
import com.arena.aid.ui.components.ArenaReasoningDrawer
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.copyToClipboard

@Composable
fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun AssistantMessage(
    msg: MessageEntity,
    live: Boolean,
    lowRam: Boolean,
    onRetry: () -> Unit,
    onCopied: (String) -> Unit
) {
    val context = LocalContext.current
    val isError = msg.content.startsWith(ERROR_PREFIX)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArenaAvatar(seed = msg.modelDisplayName, label = msg.modelDisplayName, size = 24.dp)
            Text(msg.modelDisplayName, style = MaterialTheme.typography.labelLarge)
        }
        if (msg.reasoning.isNotBlank()) ArenaReasoningDrawer(msg.reasoning, msg.latencyMs)
        when {
            isError -> ErrorCard(msg.content.removePrefix(ERROR_PREFIX), onRetry)
            msg.content.isBlank() && live -> ArenaLoader(size = 28.dp)
            else -> ArenaMarkdownView(
                markdown = msg.content,
                lowRamMode = lowRam,
                onCopied = onCopied
            )
        }
        if (!live && !isError && msg.content.isNotBlank()) {
            IconButton(onClick = {
                copyToClipboard(context, msg.content)
                onCopied("Copied")
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.ContentCopy, "Copy", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                message, Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun AgentStepsCard(steps: List<AgentStepEntity>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(4.dp)) {
            steps.forEach { step ->
                var open by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open = !open }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(step.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        ArenaStatusChip(step.status)
                        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
                    }
                    if (open) {
                        Text(
                            step.outputLog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

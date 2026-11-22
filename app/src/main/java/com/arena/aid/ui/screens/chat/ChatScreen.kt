package com.arena.aid.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.AgentStepEntity
import com.arena.aid.data.local.CodeArtifactEntity
import com.arena.aid.data.local.MessageEntity
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaModelInfo

@Composable
fun ChatScreen(
    mode: ArenaMode,
    model: ArenaModelInfo,
    messages: List<MessageEntity>,
    steps: List<AgentStepEntity>,
    artifacts: List<CodeArtifactEntity>,
    streaming: Boolean,
    lowRam: Boolean,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onModeChange: (ArenaMode) -> Unit,
    onModelClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: (MessageEntity) -> Unit,
    onToast: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val lastLen = messages.lastOrNull()?.content?.length ?: 0
    val tailIndex = messages.size + (if (steps.isNotEmpty()) 1 else 0)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) follow = false }
    }
    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward }.collect { if (it) follow = true }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) { follow = true; listState.scrollToItem(tailIndex) } }
    LaunchedEffect(lastLen) { if (follow && messages.isNotEmpty()) listState.scrollToItem(tailIndex) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) { Icon(Icons.AutoMirrored.Rounded.Notes, "History") }
            Text("Arena", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (artifacts.isNotEmpty()) {
                IconButton(onClick = onFilesClick) { Icon(Icons.Rounded.Folder, "Files") }
            }
            IconButton(onClick = onNewChat) { Icon(Icons.Rounded.Add, "New chat") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyState(mode = mode, onModeChange = onModeChange)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(messages, key = { it.id }) { m ->
                        if (m.role == "user") UserBubble(m.content)
                        else AssistantMessage(
                            msg = m,
                            live = streaming && m.id == messages.last().id,
                            lowRam = lowRam,
                            onRetry = { onRetry(m) },
                            onCopied = onToast
                        )
                    }
                    if (steps.isNotEmpty()) item(key = "steps") { AgentStepsCard(steps) }
                    item(key = "tail") { Spacer(Modifier.height(1.dp)) }
                }
            }
        }
        Composer(
            mode = mode, model = model, streaming = streaming,
            onModeChange = onModeChange, onModelClick = onModelClick,
            onSend = onSend, onStop = onStop
        )
    }
}

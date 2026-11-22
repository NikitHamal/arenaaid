package com.arena.aid.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.ChatSessionEntity
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaUserProfile
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaTextField
import com.arena.aid.ui.screens.chat.modeIcon
import java.util.Calendar

private fun bucket(ts: Long): String {
    val day = 86_400_000L
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today = cal.timeInMillis
    return when {
        ts >= today -> "Today"
        ts >= today - day -> "Yesterday"
        ts >= today - 7 * day -> "Previous 7 days"
        else -> "Older"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryDrawerContent(
    sessions: List<ChatSessionEntity>,
    activeId: String?,
    user: ArenaUserProfile?,
    onOpen: (ChatSessionEntity) -> Unit,
    onNew: () -> Unit,
    onPin: (ChatSessionEntity) -> Unit,
    onRename: (ChatSessionEntity) -> Unit,
    onDelete: (ChatSessionEntity) -> Unit,
    onSettings: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        sessions.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    }
    val pinned = filtered.filter { it.pinned }
    val groups = filtered.filter { !it.pinned }.groupBy { bucket(it.updatedAt) }
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(top = 12.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Arena", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onNew, shape = RoundedCornerShape(20.dp), color = scheme.secondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Add, null, tint = scheme.onSecondaryContainer)
                Text("New chat", style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(12.dp))
        ArenaTextField(
            value = query, onValueChange = { query = it }, placeholder = "Search history",
            leadingIcon = Icons.Rounded.Search, modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (sessions.isEmpty()) "No sessions yet" else "No matches",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (pinned.isNotEmpty()) {
                item { SectionLabel("Pinned") }
                items(pinned, key = { "p" + it.id }) { SessionRow(it, it.id == activeId, onOpen, onPin, onRename, onDelete) }
            }
            groups.forEach { (label, list) ->
                item(key = "h$label") { SectionLabel(label) }
                items(list, key = { it.id }) { SessionRow(it, it.id == activeId, onOpen, onPin, onRename, onDelete) }
            }
        }
        Surface(onClick = onSettings, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArenaAvatar(seed = user?.email ?: "arena", label = user?.name ?: "Arena", avatarUrl = user?.avatarUrl, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(user?.name ?: "Arena", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.Settings, "Settings", tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    s: ChatSessionEntity, active: Boolean,
    onOpen: (ChatSessionEntity) -> Unit, onPin: (ChatSessionEntity) -> Unit,
    onRename: (ChatSessionEntity) -> Unit, onDelete: (ChatSessionEntity) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val mode = runCatching { ArenaMode.valueOf(s.mode) }.getOrDefault(ArenaMode.CHAT)
    Box {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (active) scheme.secondaryContainer else scheme.surface,
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(s) }, onLongClick = { menu = true })
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(modeIcon(mode), null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.pinned) Icon(Icons.Rounded.PushPin, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (s.pinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                onClick = { menu = false; onPin(s) })
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { menu = false; onRename(s) })
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menu = false; onDelete(s) })
        }
    }
}

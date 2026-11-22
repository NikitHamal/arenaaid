package com.arena.aid.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.CodeArtifactEntity
import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaCodeBlockCard
import com.arena.aid.ui.components.ArenaModalSheet
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.ArenaTextField

@Composable
fun ModelSheet(
    mode: ArenaMode,
    selected: ArenaModelInfo,
    onSelect: (ArenaModelInfo) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val liveModels by ArenaCatalog.liveModels.collectAsState()
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val modeModels = remember(liveModels, mode, query) {
        val base = ArenaCatalog.modelsForMode(mode)
        val q = query.trim().lowercase()
        if (q.isEmpty()) base
        else base.filter { it.displayName.lowercase().contains(q) || it.provider.lowercase().contains(q) }
    }

    ArenaModalSheet(onDismiss = onDismiss, title = "${mode.label} models") {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArenaTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search arena.ai models",
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Sync live models")
                }
            }
            Spacer(Modifier.height(4.dp))
            modeModels.forEach { m ->
                val sel = m.id == selected.id
                Surface(
                    onClick = { onSelect(m); onDismiss() },
                    shape = RoundedCornerShape(18.dp),
                    color = if (sel) scheme.secondaryContainer else scheme.surfaceContainerLow
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ArenaAvatar(seed = m.displayName, label = m.displayName, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                m.provider,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (m.slug == "Max") {
                            ArenaStatusChip("Router")
                        }
                        if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            ArenaTextField(
                value = custom,
                onValueChange = { custom = it },
                placeholder = "Custom model ID or slug",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            ArenaButton(
                "Use custom model",
                { onSelect(ArenaCatalog.byId(custom.trim())); onDismiss() },
                Modifier.fillMaxWidth(),
                tone = ArenaButtonTone.Tonal,
                enabled = custom.isNotBlank()
            )
        }
    }
}

@Composable
fun FilesSheet(files: List<CodeArtifactEntity>, onCopied: (String) -> Unit, onDismiss: () -> Unit) {
    ArenaModalSheet(onDismiss = onDismiss, title = "Files") {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            files.forEach { f ->
                ArenaCodeBlockCard(language = f.language, code = f.content, filename = f.filename, onCopied = onCopied)
            }
        }
    }
}

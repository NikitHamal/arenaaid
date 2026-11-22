package com.arena.aid.ui.screens.code

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.CodeArtifactEntity
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaCard
import com.arena.aid.ui.components.ArenaCodeBlockCard
import com.arena.aid.ui.components.ArenaFilterChip
import com.arena.aid.ui.components.ArenaRoleBadge
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components. ArenaTextField
import com.arena.aid.ui.components.arenaHorizontalScroll
import com.arena.aid.ui.theme.ArenaCodeTextStyle

@Composable
fun CodeArenaScreen(
    artifacts: List<CodeArtifactEntity>,
    activeModel: ArenaModelInfo,
    lowRamMode: Boolean,
    isStreaming: Boolean,
    onGenerateCodePrompt: (String) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var selectedIndex by remember(artifacts.size) { mutableIntStateOf(0) }
    var showDiffMode by remember { mutableStateOf(false) }
    var customCodeTask by remember { mutableStateOf("") }

    val displayArtifacts = remember(artifacts) {
        if (artifacts.isNotEmpty()) artifacts
        else listOf(
            CodeArtifactEntity(
                id = "default_kt",
                sessionId = "seed",
                filename = "ArenaLowRamDispatcher.kt",
                language = "kotlin",
                content = """
                    package com.arena.aid.core

                    import kotlinx.coroutines.CoroutineDispatcher
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.flow.Flow
                    import kotlinx.coroutines.flow.flow
                    import kotlinx.coroutines.flow.flowOn

                    /**
                     * Coalesces high-frequency SSE token streams into 28ms frame batches
                     * so 4GB RAM / Android Go devices maintain 60fps UI responsiveness.
                     */
                    class ArenaLowRamDispatcher(
                        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
                    ) {
                        fun streamBatchedTokens(tokens: List<String>): Flow<String> = flow {
                            val buffer = StringBuilder()
                            for (token in tokens) {
                                buffer.append(token)
                                if (buffer.length >= 24) {
                                    emit(buffer.toString())
                                    buffer.clear()
                                }
                            }
                            if (buffer.isNotEmpty()) emit(buffer.toString())
                        }.flowOn(ioDispatcher)
                    }
                """.trimIndent(),
                modelName = activeModel.displayName,
                slot = "A",
                updatedAt = System.currentTimeMillis()
            ),
            CodeArtifactEntity(
                id = "default_sql",
                sessionId = "seed",
                filename = "arena_schema.sql",
                language = "sql",
                content = """
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        model_a_id TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    );

                    CREATE INDEX IF NOT EXISTS idx_sessions_updated
                    ON chat_sessions(updated_at DESC);
                """.trimIndent(),
                modelName = activeModel.displayName,
                slot = "B",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    val safeIndex = selectedIndex.coerceIn(0, (displayArtifacts.size - 1).coerceAtLeast(0))
    val currentArtifact = displayArtifacts[safeIndex]

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Card
        ArenaCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surfaceContainerLowest,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Code Arena Workspace",
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.onSurface
                            )
                            ArenaRoleBadge(text = activeModel.displayName)
                        }
                        Text(
                            text = "${displayArtifacts.size} generated files • Native syntax engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    ArenaFilterChip(
                        label = if (showDiffMode) "Split diff" else "Code view",
                        selected = showDiffMode,
                        leadingIcon = Icons.Outlined.Compare,
                        onClick = { showDiffMode = !showDiffMode }
                    )
                }

                // Quick Coding Task Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArenaTextField(
                        value = customCodeTask,
                        onValueChange = { customCodeTask = it },
                        placeholder = "Generate or refactor code (e.g. Room DAO, REST client)...",
                        leadingIcon = Icons.Outlined.Code,
                        modifier = Modifier.weight(1f)
                    )
                    ArenaButton(
                        text = "Build",
                        loading = isStreaming,
                        tone = ArenaButtonTone.Primary,
                        height = 48.dp,
                        onClick = {
                            val task = customCodeTask.trim().ifBlank {
                                "Refactor ${currentArtifact.filename} for maximum throughput and add unit tests"
                            }
                            customCodeTask = ""
                            onGenerateCodePrompt(task)
                        }
                    )
                }

                // Quick Refactor Actions
                Row(
                    modifier = Modifier.arenaHorizontalScroll(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickActions = listOf(
                        Triple("Optimize 4GB RAM", Icons.Outlined.Speed, "Optimize ${currentArtifact.filename} for 4GB RAM Android Go with zero allocations"),
                        Triple("Fix Bugs & Edge Cases", Icons.Outlined.BugReport, "Audit ${currentArtifact.filename} for null-safety, race conditions, and edge cases"),
                        Triple("Generate Unit Tests", Icons.Outlined.AutoFixHigh, "Write comprehensive JUnit4 + Coroutines test suite for ${currentArtifact.filename}")
                    )
                    quickActions.forEach { (label, icon, prompt) ->
                        ArenaStatusChip(
                            label = label,
                            leadingIcon = icon,
                            containerColor = scheme.secondaryContainer,
                            contentColor = scheme.onSecondaryContainer,
                            modifier = Modifier.clickable { onGenerateCodePrompt(prompt) }
                        )
                    }
                }
            }
        }

        // File Artifact Tabs
        Row(
            modifier = Modifier.arenaHorizontalScroll(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayArtifacts.forEachIndexed { idx, item ->
                ArenaFilterChip(
                    label = item.filename,
                    selected = idx == safeIndex,
                    leadingIcon = Icons.Outlined.Description,
                    onClick = { selectedIndex = idx }
                )
            }
        }

        // Code Viewer or Unified Diff Comparison
        if (showDiffMode && displayArtifacts.size >= 2) {
            val first = displayArtifacts[0]
            val second = displayArtifacts[1]
            ArenaCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = scheme.surfaceContainer,
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unified Diff: ${first.filename} vs ${second.filename}",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                        ArenaStatusChip(
                            label = "Correct",
                            containerColor = scheme.tertiaryContainer,
                            contentColor = scheme.onTertiaryContainer
                        )
                    }
                    val linesA = first.content.lines()
                    val linesB = second.content.lines()
                    val maxLines = maxOf(linesA.size, linesB.size).coerceAtMost(40)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .arenaHorizontalScroll(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (i in 0 until maxLines) {
                            val a = linesA.getOrNull(i)
                            val b = linesB.getOrNull(i)
                            if (a == b && a != null) {
                                Text(
                                    text = "  $a",
                                    style = ArenaCodeTextStyle,
                                    color = scheme.onSurfaceVariant
                                )
                            } else {
                                if (a != null) {
                                    Text(
                                        text = "- $a",
                                        style = ArenaCodeTextStyle.copy(fontFamily = FontFamily.Monospace),
                                        color = scheme.error,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(scheme.errorContainer.copy(alpha = 0.35f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                                if (b != null) {
                                    Text(
                                        text = "+ $b",
                                        style = ArenaCodeTextStyle.copy(fontFamily = FontFamily.Monospace),
                                        color = scheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(scheme.primaryContainer.copy(alpha = 0.45f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            ArenaCodeBlockCard(
                language = currentArtifact.language,
                code = currentArtifact.content,
                filename = "${currentArtifact.filename} (${currentArtifact.modelName})",
                showLineNumbers = true,
                lowRamMode = lowRamMode,
                onCopied = onToast
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

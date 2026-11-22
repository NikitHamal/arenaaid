package com.arena.aid.ui.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.AgentStepEntity
import com.arena.aid.data.local.MessageEntity
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaCard
import com.arena.aid.ui.components.ArenaLoader
import com.arena.aid.ui.components.ArenaMarkdownView
import com.arena.aid.ui.components.ArenaRoleBadge
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.ArenaTextField
import com.arena.aid.ui.components.arenaHorizontalScroll
import com.arena.aid.ui.theme.ArenaCodeTextStyle

@Composable
fun AgentArenaScreen(
    steps: List<AgentStepEntity>,
    messages: List<MessageEntity>,
    activeModel: ArenaModelInfo,
    isStreaming: Boolean,
    lowRamMode: Boolean,
    onRunAgentTask: (String) -> Unit,
    onOpenCodeArtifact: (String, String, String?) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var taskGoal by remember { mutableStateOf("") }

    val displaySteps = remember(steps) {
        if (steps.isNotEmpty()) steps
        else listOf(
            AgentStepEntity(
                id = "seed_1",
                sessionId = "seed",
                stepIndex = 1,
                toolName = "web_search",
                title = "Query Arena Agent benchmarks & docs",
                inputSummary = "query: \"Arena.ai Agent Mode multi-step tool execution\"",
                outputLog = "1. [Arena Agent Mode] Supports web_search, bash_sandbox, file_write, and deep_research across frontier models.",
                status = "Delivered",
                durationMs = 142L,
                createdAt = System.currentTimeMillis()
            ),
            AgentStepEntity(
                id = "seed_2",
                sessionId = "seed",
                stepIndex = 2,
                toolName = "bash_sandbox",
                title = "Inspect mobile runtime memory & CPU budget",
                inputSummary = "free -m && getprop ro.config.low_ram",
                outputLog = "MemTotal: 3940 MB | MemAvailable: 2480 MB | Native Compose pipeline active — exit 0",
                status = "Solved",
                durationMs = 96L,
                createdAt = System.currentTimeMillis()
            ),
            AgentStepEntity(
                id = "seed_3",
                sessionId = "seed",
                stepIndex = 3,
                toolName = "file_write",
                title = "Generate verified Kotlin implementation",
                inputSummary = "path: workspace/AgentCoordinator.kt",
                outputLog = "Wrote 48 lines with full coroutine cancellation safety.",
                status = "Autopilot",
                durationMs = 118L,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    val latestAssistantMessage = remember(messages) {
        messages.lastOrNull { it.role == "assistant" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Autonomous Goal Launcher Card
        ArenaCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surfaceContainerLowest,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                text = "Agent Arena",
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.onSurface
                            )
                            ArenaRoleBadge(text = activeModel.displayName)
                        }
                        Text(
                            text = "Multi-step tool execution: web search, bash sandbox, file write",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    ArenaStatusChip(
                        label = "Autopilot",
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        leadingIcon = Icons.Outlined.SmartToy
                    )
                }

                ArenaTextField(
                    value = taskGoal,
                    onValueChange = { taskGoal = it },
                    placeholder = "Describe a multi-step research or coding task...",
                    leadingIcon = Icons.Outlined.Terminal,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    modifier = Modifier.arenaHorizontalScroll(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presetTasks = listOf(
                        "Deep Research: Kotlin Compose vs WebView RAM",
                        "Build Offline Sync Queue with Room WAL",
                        "Audit API Security & OAuth Token Refresh"
                    )
                    presetTasks.forEach { preset ->
                        ArenaStatusChip(
                            label = preset,
                            containerColor = scheme.secondaryContainer,
                            contentColor = scheme.onSecondaryContainer,
                            modifier = Modifier.clickable { onRunAgentTask(preset) }
                        )
                    }
                }

                ArenaButton(
                    text = "Run autonomous agent",
                    leadingIcon = Icons.Outlined.PlayArrow,
                    loading = isStreaming,
                    tone = ArenaButtonTone.Primary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val goal = taskGoal.trim().ifBlank {
                            "Research and implement a low-memory streaming cache for Android Go"
                        }
                        taskGoal = ""
                        onRunAgentTask(goal)
                    }
                )
            }
        }

        // Tool Execution Timeline
        Text(
            text = "Tool execution timeline (${displaySteps.size} steps)",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface
        )

        displaySteps.forEach { step ->
            val toolIcon = when (step.toolName) {
                "web_search" -> Icons.Outlined.Public
                "bash_sandbox" -> Icons.Outlined.Terminal
                "file_write" -> Icons.Outlined.Description
                else -> Icons.Outlined.Science
            }
            val (chipBg, chipFg) = when (step.status) {
                "Solved" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                "Autopilot" -> scheme.primaryContainer to scheme.onPrimaryContainer
                else -> scheme.secondaryContainer to scheme.onSecondaryContainer
            }

            ArenaCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = scheme.surfaceContainerLowest,
                contentPadding = PaddingValues(14.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = toolIcon,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "${step.stepIndex}. ${step.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.onSurface
                                )
                                Text(
                                    text = "${step.toolName} • ${step.durationMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                        ArenaStatusChip(
                            label = step.status,
                            containerColor = chipBg,
                            contentColor = chipFg
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceContainer)
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "$ ${step.inputSummary}",
                                style = ArenaCodeTextStyle,
                                color = scheme.primary
                            )
                            Text(
                                text = step.outputLog,
                                style = ArenaCodeTextStyle,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isStreaming) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArenaLoader(size = 24.dp)
                Text(
                    text = "Agent executing tools and compiling report...",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        if (latestAssistantMessage != null) {
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
                        Text(
                            text = "Agent Deliverable",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface
                        )
                        ArenaStatusChip(
                            label = "Delivered",
                            containerColor = scheme.tertiaryContainer,
                            contentColor = scheme.onTertiaryContainer
                        )
                    }
                    ArenaMarkdownView(
                        markdown = latestAssistantMessage.content,
                        lowRamMode = lowRamMode,
                        onOpenInCodeArena = onOpenCodeArtifact,
                        onCopied = onToast
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

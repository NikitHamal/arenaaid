package com.arena.aid.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.ChatSessionEntity
import com.arena.aid.data.local.MessageEntity
import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaChatSubMode
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaCard
import com.arena.aid.ui.components.ArenaFilterChip
import com.arena.aid.ui.components.ArenaLoader
import com.arena.aid.ui.components.ArenaMarkdownView
import com.arena.aid.ui.components.ArenaModalSheet
import com.arena.aid.ui.components.ArenaReasoningDrawer
import com.arena.aid.ui.components.ArenaRoleBadge
import com.arena.aid.ui.components.ArenaSegmentedSwitch
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.arenaHorizontalScroll
import com.arena.aid.ui.components.copyToClipboard
import com.arena.aid.ui.theme.arenaFieldColor

private data class TurnPair(
    val userMessage: MessageEntity,
    val modelAMessage: MessageEntity?,
    val modelBMessage: MessageEntity?
)

@Composable
fun ChatAndBattleScreen(
    subMode: ArenaChatSubMode,
    onSelectSubMode: (ArenaChatSubMode) -> Unit,
    modelA: ArenaModelInfo,
    modelB: ArenaModelInfo,
    onSelectModelA: (ArenaModelInfo) -> Unit,
    onSelectModelB: (ArenaModelInfo) -> Unit,
    currentSession: ChatSessionEntity?,
    messages: List<MessageEntity>,
    isStreaming: Boolean,
    lowRamMode: Boolean,
    onSendPrompt: (String) -> Unit,
    onVoteBattle: (String) -> Unit,
    onOpenCodeArtifact: (String, String, String?) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var promptText by remember { mutableStateOf("") }
    var activeSlotTab by remember { mutableStateOf("A") }
    var pickingModelForSlot by remember { mutableStateOf<String?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }

    val turns = remember(messages) {
        val list = mutableListOf<TurnPair>()
        var currentUser: MessageEntity? = null
        var currentA: MessageEntity? = null
        var currentB: MessageEntity? = null
        for (m in messages) {
            if (m.role == "user") {
                if (currentUser != null) {
                    list.add(TurnPair(currentUser, currentA, currentB))
                }
                currentUser = m
                currentA = null
                currentB = null
            } else if (m.slot == "B") {
                currentB = m
            } else {
                currentA = m
            }
        }
        if (currentUser != null) {
            list.add(TurnPair(currentUser, currentA, currentB))
        }
        list
    }

    LaunchedEffect(turns.size, isStreaming) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    val modelsRevealed = currentSession?.modelsRevealed ?: (subMode != ArenaChatSubMode.BATTLE)
    val battleVote = currentSession?.battleVote

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        // Sub-mode switcher (Battle / Side by side / Direct) + Model selector pills
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArenaSegmentedSwitch(
                options = ArenaChatSubMode.entries,
                selected = subMode,
                onSelect = onSelectSubMode,
                labelFor = { it.label },
                iconFor = {
                    when (it) {
                        ArenaChatSubMode.BATTLE -> Icons.Outlined.Balance
                        ArenaChatSubMode.SIDE_BY_SIDE -> Icons.Outlined.CompareArrows
                        ArenaChatSubMode.DIRECT -> Icons.Outlined.Bolt
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Model Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (subMode) {
                    ArenaChatSubMode.DIRECT -> {
                        ModelPickerPill(
                            prefix = "Model",
                            name = modelA.displayName,
                            elo = "${modelA.elo} Elo",
                            onClick = { pickingModelForSlot = "A" },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ArenaChatSubMode.SIDE_BY_SIDE -> {
                        ModelPickerPill(
                            prefix = "A",
                            name = modelA.displayName,
                            elo = "${modelA.elo}",
                            onClick = { pickingModelForSlot = "A" },
                            modifier = Modifier.weight(1f)
                        )
                        ModelPickerPill(
                            prefix = "B",
                            name = modelB.displayName,
                            elo = "${modelB.elo}",
                            onClick = { pickingModelForSlot = "B" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ArenaChatSubMode.BATTLE -> {
                        ArenaCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = scheme.surfaceContainerLow,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.HowToVote,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (modelsRevealed) {
                                            "Revealed: ${modelA.displayName} vs ${modelB.displayName}"
                                        } else {
                                            "Anonymous blind battle: Assistant A vs Assistant B"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ArenaStatusChip(
                                    label = if (modelsRevealed) "Solved" else "Blind",
                                    containerColor = if (modelsRevealed) scheme.tertiaryContainer else scheme.secondaryContainer,
                                    contentColor = if (modelsRevealed) scheme.onTertiaryContainer else scheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Conversation Stream or Empty State Starter Prompts
        if (turns.isEmpty()) {
            ChatEmptyStarterState(
                subMode = subMode,
                modelA = modelA,
                onPickPrompt = { starter -> onSendPrompt(starter) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items = turns, key = { it.userMessage.id }) { turn ->
                    TurnConversationItem(
                        turn = turn,
                        subMode = subMode,
                        modelsRevealed = modelsRevealed,
                        battleVote = battleVote,
                        activeSlotTab = activeSlotTab,
                        onSelectSlotTab = { activeSlotTab = it },
                        lowRamMode = lowRamMode,
                        onVoteBattle = onVoteBattle,
                        onOpenCodeArtifact = onOpenCodeArtifact,
                        onToast = onToast
                    )
                }
                if (isStreaming) {
                    item(key = "streaming_indicator") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArenaLoader(size = 24.dp)
                            Text(
                                text = "Streaming response with low-RAM frame coalescing...",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // M3X Composer Bar
        ArenaComposerBar(
            value = promptText,
            onValueChange = { promptText = it },
            showAttachMenu = showAttachMenu,
            onToggleAttachMenu = { showAttachMenu = !showAttachMenu },
            onInsertTemplate = { snippet ->
                promptText = if (promptText.isBlank()) snippet else "$promptText\n$snippet"
                showAttachMenu = false
            },
            isStreaming = isStreaming,
            onSend = {
                if (promptText.isNotBlank()) {
                    val toSend = promptText
                    promptText = ""
                    showAttachMenu = false
                    onSendPrompt(toSend)
                }
            }
        )
    }

    if (pickingModelForSlot != null) {
        ModelSelectionSheet(
            slot = pickingModelForSlot!!,
            currentModel = if (pickingModelForSlot == "B") modelB else modelA,
            onDismiss = { pickingModelForSlot = null },
            onSelect = { chosen ->
                if (pickingModelForSlot == "B") onSelectModelB(chosen) else onSelectModelA(chosen)
                pickingModelForSlot = null
            }
        )
    }
}

@Composable
private fun ModelPickerPill(
    prefix: String,
    name: String,
    elo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            ArenaStatusChip(
                label = prefix,
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = elo,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Change model",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ChatEmptyStarterState(
    subMode: ArenaChatSubMode,
    modelA: ArenaModelInfo,
    onPickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val starters = listOf(
        Triple(
            "Kotlin Compose M3X State Holder",
            "Write a lock-free Kotlin StateFlow coordinator for a 4GB RAM Android app with unit test.",
            Icons.Outlined.Code
        ),
        Triple(
            "Compare SQL Indexing Strategies",
            "Compare B-Tree vs GIN indexing in PostgreSQL for high-write chat message tables.",
            Icons.Outlined.Science
        ),
        Triple(
            "Debug Low-Memory Android Go ANR",
            "Diagnose why WebView causes GC thrashing on a 4GB RAM Unisoc phone and show the native Compose fix.",
            Icons.Outlined.Memory
        ),
        Triple(
            "Fullstack Auth API Route",
            "Build a Next.js App Router + Drizzle ORM session validation route with token rotation.",
            Icons.Outlined.Terminal
        )
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArenaCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surfaceContainerLowest,
            contentPadding = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArenaAvatar(seed = modelA.slug, label = modelA.displayName, size = 42.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = when (subMode) {
                                    ArenaChatSubMode.BATTLE -> "Arena Blind Battle"
                                    ArenaChatSubMode.SIDE_BY_SIDE -> "Side-by-Side Comparison"
                                    ArenaChatSubMode.DIRECT -> modelA.displayName
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.onSurface
                            )
                            ArenaRoleBadge(text = "${modelA.elo} Elo")
                        }
                        Text(
                            text = "${modelA.provider} • ${modelA.contextWindow} context • ${modelA.speedTokensPerSec} tok/s",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                starters.forEach { (title, prompt, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(scheme.surfaceContainerLow)
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(18.dp))
                            .clickable { onPickPrompt(prompt) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurface
                            )
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnConversationItem(
    turn: TurnPair,
    subMode: ArenaChatSubMode,
    modelsRevealed: Boolean,
    battleVote: String?,
    activeSlotTab: String,
    onSelectSlotTab: (String) -> Unit,
    lowRamMode: Boolean,
    onVoteBattle: (String) -> Unit,
    onOpenCodeArtifact: (String, String, String?) -> Unit,
    onToast: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // User Prompt Bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Text(
                    text = turn.userMessage.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                    color = scheme.onPrimaryContainer
                )
            }
        }

        val hasDualResponses = (subMode == ArenaChatSubMode.BATTLE || subMode == ArenaChatSubMode.SIDE_BY_SIDE) &&
            turn.modelBMessage != null

        if (hasDualResponses) {
            val labelA = if (modelsRevealed) {
                "A: ${turn.modelAMessage?.modelDisplayName ?: "Model A"}"
            } else "Assistant A"
            val labelB = if (modelsRevealed) {
                "B: ${turn.modelBMessage?.modelDisplayName ?: "Model B"}"
            } else "Assistant B"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArenaFilterChip(
                    label = labelA,
                    selected = activeSlotTab == "A",
                    onClick = { onSelectSlotTab("A") },
                    modifier = Modifier.weight(1f)
                )
                ArenaFilterChip(
                    label = labelB,
                    selected = activeSlotTab == "B",
                    onClick = { onSelectSlotTab("B") },
                    modifier = Modifier.weight(1f)
                )
            }

            val displayed = if (activeSlotTab == "B") turn.modelBMessage else turn.modelAMessage
            if (displayed != null) {
                AssistantResponseCard(
                    message = displayed,
                    revealed = modelsRevealed,
                    slotLabel = if (activeSlotTab == "B") "Assistant B" else "Assistant A",
                    lowRamMode = lowRamMode,
                    onOpenCodeArtifact = onOpenCodeArtifact,
                    onCopy = {
                        copyToClipboard(context, displayed.content)
                        onToast("Copied response")
                    }
                )
            }

            if (subMode == ArenaChatSubMode.BATTLE) {
                BattleVoteBar(
                    currentVote = battleVote,
                    modelAName = turn.modelAMessage?.modelDisplayName ?: "Model A",
                    modelBName = turn.modelBMessage?.modelDisplayName ?: "Model B",
                    onVote = onVoteBattle
                )
            }
        } else if (turn.modelAMessage != null) {
            AssistantResponseCard(
                message = turn.modelAMessage,
                revealed = true,
                slotLabel = turn.modelAMessage.modelDisplayName,
                lowRamMode = lowRamMode,
                onOpenCodeArtifact = onOpenCodeArtifact,
                onCopy = {
                    copyToClipboard(context, turn.modelAMessage.content)
                    onToast("Copied response")
                }
            )
        }
    }
}

@Composable
private fun AssistantResponseCard(
    message: MessageEntity,
    revealed: Boolean,
    slotLabel: String,
    lowRamMode: Boolean,
    onOpenCodeArtifact: (String, String, String?) -> Unit,
    onCopy: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val title = if (revealed) message.modelDisplayName else slotLabel

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArenaAvatar(seed = title, label = title, size = 30.dp)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    if (revealed) {
                        ArenaRoleBadge(text = "Verified")
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArenaStatusChip(
                        label = "${message.tokensPerSec} tok/s",
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surfaceContainerLow)
                            .clickable(onClick = onCopy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            if (message.reasoning.isNotBlank()) {
                ArenaReasoningDrawer(
                    reasoning = message.reasoning,
                    durationMs = message.latencyMs
                )
            }

            ArenaMarkdownView(
                markdown = message.content,
                lowRamMode = lowRamMode,
                onOpenInCodeArena = onOpenCodeArtifact,
                onCopied = { }
            )
        }
    }
}

@Composable
private fun BattleVoteBar(
    currentVote: String?,
    modelAName: String,
    modelBName: String,
    onVote: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    ArenaCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = scheme.surfaceContainerLow,
        contentPadding = PaddingValues(12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (currentVote == null) {
                        "Vote to reveal anonymous models"
                    } else {
                        "A: $modelAName  •  B: $modelBName"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface
                )
                if (currentVote != null) {
                    ArenaStatusChip(
                        label = "Voted $currentVote",
                        containerColor = scheme.tertiaryContainer,
                        contentColor = scheme.onTertiaryContainer
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ArenaButton(
                    text = "A is better",
                    leadingIcon = Icons.Outlined.ThumbUp,
                    tone = if (currentVote == "A") ArenaButtonTone.Primary else ArenaButtonTone.Outlined,
                    height = 38.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { onVote("A") }
                )
                ArenaButton(
                    text = "Tie",
                    tone = if (currentVote == "TIE") ArenaButtonTone.Primary else ArenaButtonTone.Outlined,
                    height = 38.dp,
                    onClick = { onVote("TIE") }
                )
                ArenaButton(
                    text = "B is better",
                    leadingIcon = Icons.Outlined.ThumbUp,
                    tone = if (currentVote == "B") ArenaButtonTone.Primary else ArenaButtonTone.Outlined,
                    height = 38.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { onVote("B") }
                )
            }
        }
    }
}

@Composable
private fun ArenaComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    showAttachMenu: Boolean,
    onToggleAttachMenu: () -> Unit,
    onInsertTemplate: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // M3X Plus Attach Popup (compact tonal tiles)
        AnimatedVisibility(visible = showAttachMenu) {
            Row(
                modifier = Modifier
                    .arenaHorizontalScroll()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceContainerLow)
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val templates = listOf(
                    "Kotlin Compose" to "Build a shadow-free M3 Expressive Jetpack Compose component for: ",
                    "Fix Bug" to "Analyze and fix the root cause of this bug with unit tests: ",
                    "SQL Schema" to "Design a normalized PostgreSQL schema and Drizzle query for: ",
                    "Benchmark" to "Compare time and memory complexity (Big-O) for: "
                )
                templates.forEach { (label, text) ->
                    ArenaStatusChip(
                        label = label,
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        leadingIcon = Icons.Outlined.AutoAwesome,
                        modifier = Modifier.clickable { onInsertTemplate(text) }
                    )
                }
            }
        }

        // M3X Outlined Composer Container (24dp radius)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(arenaFieldColor())
                .border(1.5.dp, scheme.outlineVariant, RoundedCornerShape(24.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Plus button that turns into a cross while open
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (showAttachMenu) scheme.primaryContainer else scheme.surfaceContainerLow
                    )
                    .clickable(onClick = onToggleAttachMenu),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showAttachMenu) Icons.Outlined.Close else Icons.Outlined.Add,
                    contentDescription = "Attach prompt tool",
                    tint = if (showAttachMenu) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Ask Arena anything or paste code...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val ready = value.isNotBlank() && !isStreaming
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (ready) scheme.primary else scheme.surfaceContainerHigh)
                    .clickable(enabled = ready, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowUpward,
                    contentDescription = "Send",
                    tint = if (ready) scheme.onPrimary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelSelectionSheet(
    slot: String,
    currentModel: ArenaModelInfo,
    onDismiss: () -> Unit,
    onSelect: (ArenaModelInfo) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    ArenaModalSheet(
        onDismiss = onDismiss,
        title = "Select Model $slot"
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(items = ArenaCatalog.models, key = { it.id }) { model ->
                val isSelected = model.id == currentModel.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) scheme.primaryContainer else scheme.surfaceContainerLow
                        )
                        .border(
                            1.dp,
                            if (isSelected) scheme.primary else scheme.outlineVariant,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelect(model) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        ArenaAvatar(seed = model.slug, label = model.displayName, size = 38.dp)
                        Column {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface
                            )
                            Text(
                                text = "${model.provider} • ${model.contextWindow} ctx • ${model.specialtyTag}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                            )
                        }
                    }
                    ArenaStatusChip(
                        label = "${model.elo} Elo",
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

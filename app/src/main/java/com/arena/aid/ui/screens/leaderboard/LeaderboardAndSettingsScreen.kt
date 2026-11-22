package com.arena.aid.ui.screens.leaderboard

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.ArenaAppSettings
import com.arena.aid.data.local.ArenaThemePreference
import com.arena.aid.data.local.ChatSessionEntity
import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaLeaderboardCategory
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.data.model.ArenaUserProfile
import com.arena.aid.data.model.DeviceRamSnapshot
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaCard
import com.arena.aid.ui.components.ArenaFilterChip
import com.arena.aid.ui.components.ArenaModalSheet
import com.arena.aid.ui.components.ArenaRoleBadge
import com.arena.aid.ui.components.ArenaSegmentedSwitch
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.ArenaTextField
import com.arena.aid.ui.components.arenaHorizontalScroll

@Composable
fun LeaderboardScreen(
    selectedCategory: ArenaLeaderboardCategory,
    onSelectCategory: (ArenaLeaderboardCategory) -> Unit,
    onStartChatWithModel: (ArenaModelInfo) -> Unit,
    onCompareWithModel: (ArenaModelInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }
    var openWeightsOnly by remember { mutableStateOf(false) }

    val filteredModels = remember(selectedCategory, searchQuery, openWeightsOnly) {
        ArenaCatalog.models
            .filter { model ->
                val matchesSearch = searchQuery.isBlank() ||
                    model.displayName.contains(searchQuery, ignoreCase = true) ||
                    model.provider.contains(searchQuery, ignoreCase = true)
                val matchesWeights = !openWeightsOnly || model.isOpenWeights
                matchesSearch && matchesWeights
            }
            .sortedByDescending { model ->
                if (model.category == selectedCategory) model.elo + 15 else model.elo
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ArenaTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search models or providers (Google, Anthropic, OpenAI, DeepSeek)...",
            leadingIcon = Icons.Outlined.Search
        )

        Row(
            modifier = Modifier.arenaHorizontalScroll(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArenaLeaderboardCategory.entries.forEach { cat ->
                ArenaFilterChip(
                    label = cat.label,
                    selected = selectedCategory == cat,
                    leadingIcon = Icons.Outlined.EmojiEvents,
                    onClick = { onSelectCategory(cat) }
                )
            }
            ArenaFilterChip(
                label = "Open weights",
                selected = openWeightsOnly,
                onClick = { openWeightsOnly = !openWeightsOnly }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(items = filteredModels, key = { _, m -> m.id }) { index, model ->
                val rank = index + 1
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (rank <= 3) scheme.primaryContainer else scheme.surfaceContainerHigh
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#$rank",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                                        color = if (rank <= 3) scheme.onPrimaryContainer else scheme.onSurface
                                    )
                                }
                                ArenaAvatar(seed = model.slug, label = model.displayName, size = 38.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = model.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = scheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        ArenaRoleBadge(
                                            text = if (model.isOpenWeights) "Open" else "Frontier"
                                        )
                                    }
                                    Text(
                                        text = "${model.provider} • ${model.votes} votes • ${model.contextWindow} ctx",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                            ArenaStatusChip(
                                label = "${model.elo} ±${model.ci}",
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArenaStatusChip(
                                label = model.specialtyTag,
                                containerColor = scheme.secondaryContainer,
                                contentColor = scheme.onSecondaryContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ArenaButton(
                                    text = "Compare",
                                    tone = ArenaButtonTone.Outlined,
                                    height = 36.dp,
                                    onClick = { onCompareWithModel(model) }
                                )
                                ArenaButton(
                                    text = "Chat",
                                    tone = ArenaButtonTone.Primary,
                                    height = 36.dp,
                                    onClick = { onStartChatWithModel(model) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistorySheet(
    sessions: List<ChatSessionEntity>,
    activeSessionId: String?,
    onSelectSession: (ChatSessionEntity) -> Unit,
    onTogglePin: (ChatSessionEntity) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartNewSession: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var filterQuery by remember { mutableStateOf("") }
    val filtered = remember(sessions, filterQuery) {
        if (filterQuery.isBlank()) sessions
        else sessions.filter { it.title.contains(filterQuery, ignoreCase = true) }
    }

    ArenaModalSheet(
        onDismiss = onDismiss,
        title = "Sessions (${sessions.size})"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArenaTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = "Search past chats, battles or code...",
                    leadingIcon = Icons.Outlined.Search,
                    modifier = Modifier.weight(1f)
                )
                ArenaButton(
                    text = "New",
                    tone = ArenaButtonTone.Primary,
                    height = 48.dp,
                    onClick = {
                        onStartNewSession()
                        onDismiss()
                    }
                )
            }

            if (filtered.isEmpty()) {
                Text(
                    text = "No saved sessions match your search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(items = filtered, key = { _, s -> s.id }) { _, session ->
                        val isCurrent = session.id == activeSessionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isCurrent) scheme.primaryContainer else scheme.surfaceContainerLow
                                )
                                .border(
                                    1.dp,
                                    if (isCurrent) scheme.primary else scheme.outlineVariant,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    onSelectSession(session)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ArenaStatusChip(
                                        label = session.mode,
                                        containerColor = scheme.secondaryContainer,
                                        contentColor = scheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isCurrent) scheme.onPrimaryContainer else scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onTogglePin(session) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = "Pin",
                                        tint = if (session.pinned) scheme.primary else scheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSession(session.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = scheme.error,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndPerformanceScreen(
    user: ArenaUserProfile?,
    settings: ArenaAppSettings,
    ramSnapshot: DeviceRamSnapshot,
    onSelectTheme: (ArenaThemePreference) -> Unit,
    onToggleLowRamTurbo: (Boolean) -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onSaveApiConfig: (cookie: String, apiUrl: String, apiKey: String) -> Unit,
    onTrimMemory: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var cookieInput by remember(user) { mutableStateOf(user?.sessionCookie.orEmpty()) }
    var apiUrlInput by remember(user) { mutableStateOf(user?.customApiUrl ?: "https://arena.ai") }
    var apiKeyInput by remember(user) { mutableStateOf(user?.customApiKey.orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Account Profile Card
        ArenaCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surfaceContainerLowest,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ArenaAvatar(
                        seed = user?.email ?: "guest",
                        label = user?.name ?: "Guest User",
                        size = 44.dp
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = user?.name ?: "Guest Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface
                            )
                            ArenaRoleBadge(text = user?.authMethod ?: "Local")
                        }
                        Text(
                            text = user?.email ?: "Sign in with Google or Email to sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                if (user == null) {
                    ArenaButton(
                        text = "Sign in",
                        tone = ArenaButtonTone.Primary,
                        height = 38.dp,
                        onClick = onOpenSignIn
                    )
                } else {
                    ArenaButton(
                        text = "Sign out",
                        leadingIcon = Icons.Outlined.Logout,
                        tone = ArenaButtonTone.Outlined,
                        height = 38.dp,
                        onClick = onSignOut
                    )
                }
            }
        }

        // 4GB RAM / Android Go Performance Engine Card
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "4GB RAM and Android Go Engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface
                        )
                    }
                    ArenaStatusChip(
                        label = "${ramSnapshot.appHeapUsedMb} MB heap",
                        containerColor = scheme.tertiaryContainer,
                        contentColor = scheme.onTertiaryContainer,
                        leadingIcon = Icons.Outlined.Speed
                    )
                }

                Text(
                    text = "Device: ${ramSnapshot.deviceModel} (${ramSnapshot.cpuCores} cores) • System RAM: ${ramSnapshot.availableRamMb} MB free / ${ramSnapshot.totalRamMb} MB total",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Low-RAM stream frame coalescing",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                        Text(
                            text = "Batches token updates to ${settings.streamFrameCoalesceMs}ms frames to prevent UI jank on 4GB RAM phones",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.lowRamTurboEnabled,
                        onCheckedChange = onToggleLowRamTurbo
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Instant snap transitions",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                        Text(
                            text = "Collapses spring animations to instant snaps for maximum battery and GPU savings",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.reducedMotion,
                        onCheckedChange = onToggleReducedMotion
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArenaButton(
                        text = "Trim RAM cache",
                        leadingIcon = Icons.Outlined.CleaningServices,
                        tone = ArenaButtonTone.Tonal,
                        modifier = Modifier.weight(1f),
                        onClick = onTrimMemory
                    )
                    ArenaButton(
                        text = "Clear history",
                        leadingIcon = Icons.Outlined.DeleteOutline,
                        tone = ArenaButtonTone.Danger,
                        modifier = Modifier.weight(1f),
                        onClick = onClearHistory
                    )
                }
            }
        }

        // Theme Switcher Card
        ArenaCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surfaceContainerLowest,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "M3 Expressive Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                ArenaSegmentedSwitch(
                    options = ArenaThemePreference.entries,
                    selected = settings.themePreference,
                    onSelect = onSelectTheme,
                    labelFor = { it.label },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Arena Session Cookie & Custom API Bridge Card
        ArenaCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surfaceContainerLowest,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Arena.ai Cookie and Custom API",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                ArenaTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    label = "arena-auth-prod-v1.0 cookie",
                    placeholder = "arena-auth-prod-v1.0=...",
                    leadingIcon = Icons.Outlined.Cookie
                )
                ArenaTextField(
                    value = apiUrlInput,
                    onValueChange = { apiUrlInput = it },
                    label = "Base API URL",
                    placeholder = "https://arena.ai"
                )
                ArenaTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = "Custom API key (OpenAI / Gemini / OpenRouter)",
                    placeholder = "sk-...",
                    leadingIcon = Icons.Outlined.Key
                )
                ArenaButton(
                    text = "Save connection settings",
                    tone = ArenaButtonTone.Primary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSaveApiConfig(cookieInput, apiUrlInput, apiKeyInput) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

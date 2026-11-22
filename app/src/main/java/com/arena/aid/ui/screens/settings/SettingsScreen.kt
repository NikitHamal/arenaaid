package com.arena.aid.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arena.aid.data.local.ArenaAppSettings
import com.arena.aid.data.local.ArenaThemePreference
import com.arena.aid.data.model.ArenaUserProfile
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaDialog
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.ArenaTextField

@Composable
fun SettingsScreen(
    user: ArenaUserProfile?,
    settings: ArenaAppSettings,
    onBack: () -> Unit,
    onConnection: () -> Unit,
    onUpdate: ((ArenaAppSettings) -> ArenaAppSettings) -> Unit,
    onClearHistory: () -> Unit,
    onSignOut: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var confirmClear by remember { mutableStateOf(false) }
    var instructions by remember { mutableStateOf(settings.systemInstructions) }
    val connected = user != null && (user.sessionCookie.isNotBlank() || user.customApiKey.isNotBlank())

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ArenaAvatar(seed = user?.email ?: "arena", label = user?.name ?: "Arena", avatarUrl = user?.avatarUrl, size = 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(user?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Connection", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    ArenaStatusChip(if (connected) "Connected" else "Not connected")
                }
                Spacer(Modifier.height(12.dp))
                ArenaButton("Manage connection", onConnection, Modifier.fillMaxWidth(), tone = ArenaButtonTone.Tonal)
            }
            Card {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArenaThemePreference.entries.forEach { t ->
                        val sel = t == settings.themePreference
                        Surface(
                            onClick = { onUpdate { it.copy(themePreference = t) } },
                            shape = RoundedCornerShape(18.dp),
                            color = if (sel) scheme.secondaryContainer else scheme.surfaceContainerHighest
                        ) {
                            Text(
                                t.label, Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sel) scheme.onSecondaryContainer else scheme.onSurface
                            )
                        }
                    }
                }
            }
            Card {
                ToggleRow("Low memory mode", settings.lowRamTurboEnabled) { v -> onUpdate { it.copy(lowRamTurboEnabled = v) } }
                Spacer(Modifier.height(4.dp))
                ToggleRow("Reduce motion", settings.reducedMotion) { v -> onUpdate { it.copy(reducedMotion = v) } }
            }
            Card {
                Text("Custom instructions", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                ArenaTextField(
                    value = instructions,
                    onValueChange = { instructions = it; onUpdate { s -> s.copy(systemInstructions = it) } },
                    singleLine = false, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth()
                )
            }
            ArenaButton("Clear history", { confirmClear = true }, Modifier.fillMaxWidth(), tone = ArenaButtonTone.Outlined)
            ArenaButton(
                "Sign out", onSignOut, Modifier.fillMaxWidth(), tone = ArenaButtonTone.Danger,
                leadingIcon = Icons.AutoMirrored.Rounded.Logout
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmClear) {
        ArenaDialog(
            onDismiss = { confirmClear = false }, title = "Clear all history?", confirmLabel = "Clear",
            destructive = true, onConfirm = { confirmClear = false; onClearHistory() }
        ) { Text("All sessions on this device will be deleted.", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

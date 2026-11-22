package com.arena.aid.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.ui.components.ArenaAvatar

@Composable
fun EmptyState(mode: ArenaMode, onModeChange: (ArenaMode) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArenaAvatar(seed = "arena", label = "Arena", size = 56.dp)
        Spacer(Modifier.height(20.dp))
        Text("What can I help with?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArenaMode.entries.forEach { m ->
                val selected = m == mode
                val scheme = MaterialTheme.colorScheme
                Surface(
                    onClick = { onModeChange(m) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            modeIcon(m), null, Modifier.size(24.dp),
                            tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                        )
                        Text(
                            m.label, style = MaterialTheme.typography.labelLarge,
                            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

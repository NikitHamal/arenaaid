package com.arena.aid.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaModelInfo

fun modeIcon(mode: ArenaMode): ImageVector = when (mode) {
    ArenaMode.CHAT -> Icons.Rounded.ChatBubbleOutline
    ArenaMode.CODE -> Icons.Rounded.Code
    ArenaMode.AGENT -> Icons.Rounded.AutoAwesome
}

@Composable
fun Composer(
    mode: ArenaMode,
    model: ArenaModelInfo,
    streaming: Boolean,
    onModeChange: (ArenaMode) -> Unit,
    onModelClick: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val canSend = text.isNotBlank() && !streaming

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                decorationBox = { inner ->
                    Box(Modifier.padding(end = 6.dp)) {
                        if (text.isEmpty()) {
                            Text(
                                "Message Arena",
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    PillButton(modeIcon(mode), mode.label, true, onClick = { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ArenaMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                leadingIcon = { Icon(modeIcon(m), null) },
                                onClick = { menu = false; onModeChange(m) }
                            )
                        }
                    }
                }
                PillButton(null, model.displayName, false, onModelClick, Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = {
                        if (streaming) onStop() else if (canSend) { onSend(text); text = "" }
                    },
                    shape = CircleShape,
                    color = if (streaming || canSend) scheme.primary else scheme.surfaceContainerHighest,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (streaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                            contentDescription = if (streaming) "Stop" else "Send",
                            tint = if (streaming || canSend) scheme.onPrimary else scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    icon: ImageVector?,
    label: String,
    tonal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (tonal) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tint = if (tonal) scheme.onSecondaryContainer else scheme.onSurface
            if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = tint)
            Text(
                label, style = MaterialTheme.typography.labelLarge, color = tint,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)
            )
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = tint)
        }
    }
}

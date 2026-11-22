@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.arena.aid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import coil.compose.AsyncImage

object AvatarShapes {
    val polygons: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Sunny,
            MaterialShapes.Flower,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.SoftBurst,
            MaterialShapes.Oval,
            MaterialShapes.Square
        )
    }

    private fun hash(seed: String): Long {
        var h = 2166136261L
        for (c in seed) {
            h = h xor c.code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return h
    }

    fun indexFor(seed: String): Int {
        if (seed.isBlank()) return -1
        return (hash(seed.trim().lowercase()) % polygons.size).toInt()
    }

    fun toneFor(seed: String): Int {
        if (seed.isBlank()) return 0
        return ((hash(seed.trim().lowercase()) ushr 8) % 3).toInt()
    }
}

@Composable
fun rememberAvatarShape(seed: String): Shape {
    val index = remember(seed) { AvatarShapes.indexFor(seed) }
    val shaped = AvatarShapes.polygons[index.coerceAtLeast(0)].toShape()
    return if (index < 0) CircleShape else shaped
}

@Composable
fun avatarTone(seed: String): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val tone = remember(seed) { AvatarShapes.toneFor(seed) }
    return when (tone) {
        1 -> c.tertiaryContainer to c.onTertiaryContainer
        2 -> c.primaryContainer to c.onPrimaryContainer
        else -> c.secondaryContainer to c.onSecondaryContainer
    }
}

/**
 * Frameless M3X expressive shape avatar. Zero border ring, zero shadow.
 */
@Composable
fun ArenaAvatar(
    seed: String,
    label: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    size: Dp = 38.dp
) {
    val shape = rememberAvatarShape(seed.ifBlank { label })
    val (bg, fg) = avatarTone(seed.ifBlank { label })
    val initials = remember(label) {
        label.trim()
            .split(" ", "-", "_")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "A" }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.W700,
                    fontSize = (size.value * 0.36f).sp
                ),
                color = fg
            )
        }
    }
}

/**
 * M3X Rule: User/Role/Verification badges have NO background fill and NO border.
 */
@Composable
fun ArenaRoleBadge(
    text: String,
    icon: ImageVector = Icons.Filled.Verified,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * M3X Rule: Filter pills, category tags, and status labels (Solved, Answer, Correct, Helpful,
 * Autopilot, Delivered) DO keep M3E tonal surface backgrounds.
 */
@Composable
fun ArenaStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * M3X Filter Chip: 40dp tall, 20dp radius, surface with 1dp outlineVariant when unselected;
 * secondaryContainer fill with leading check and no border when selected.
 */
@Composable
fun ArenaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    count: Int? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val bg = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLowest
    val fg = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (!selected) Modifier.border(1.dp, scheme.outlineVariant, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(15.dp)
            )
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) fg else scheme.onSurfaceVariant
            )
        }
    }
}

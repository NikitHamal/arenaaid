package com.arena.aid.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val ArenaShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalLowRamReducedMotion = staticCompositionLocalOf { false }

/**
 * Floating surface fill: pure white in light mode, raised surfaceContainerHigh in dark mode.
 * Zero elevation, bordered by [arenaFloatLine].
 */
@Composable
@ReadOnlyComposable
fun arenaFloatColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() > 0.5f) Color.White else scheme.surfaceContainerHigh
}

@Composable
@ReadOnlyComposable
fun arenaFloatLine(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
@ReadOnlyComposable
fun arenaFieldColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() > 0.5f) Color.White else scheme.surfaceContainerLowest
}

@Composable
fun arenaAnimationsReduced(): Boolean {
    if (LocalLowRamReducedMotion.current) return true
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        return !android.animation.ValueAnimator.areAnimatorsEnabled()
    }
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> arenaFastSpatialSpec(): FiniteAnimationSpec<T> =
    if (arenaAnimationsReduced()) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> arenaSpatialSpec(): FiniteAnimationSpec<T> =
    if (arenaAnimationsReduced()) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> arenaSlowSpatialSpec(): FiniteAnimationSpec<T> =
    if (arenaAnimationsReduced()) snap() else MaterialTheme.motionScheme.slowSpatialSpec()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> arenaFastEffectsSpec(): FiniteAnimationSpec<T> =
    if (arenaAnimationsReduced()) snap() else MaterialTheme.motionScheme.fastEffectsSpec()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> arenaEffectsSpec(): FiniteAnimationSpec<T> =
    if (arenaAnimationsReduced()) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

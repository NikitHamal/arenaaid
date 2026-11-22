package com.arena.aid.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Guarantees a bounded width on LazyRow / HorizontalPager / horizontalScroll containers so
 * nesting inside a flexible row never crashes with infinite max width constraints.
 */
fun Modifier.boundedWidth(): Modifier = this.fillMaxWidth()

@Composable
fun Modifier.arenaHorizontalScroll(
    state: ScrollState = rememberScrollState(),
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
): Modifier = this
    .boundedWidth()
    .horizontalScroll(
        state = state,
        enabled = enabled,
        flingBehavior = flingBehavior,
        reverseScrolling = reverseScrolling
    )

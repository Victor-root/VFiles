/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.zhanghai.android.files.theme.StatusBarBackgroundEffect
import kotlin.math.roundToInt

/**
 * How far an app bar hiding on scroll has moved up, from 0 to minus its height. It moves by as much
 * as the content scrolls, like the content pushed it.
 */
@Stable
class AppBarScrollState(private val coroutineScope: CoroutineScope) {
    var isEnabled = true

    var height by mutableFloatStateOf(0f)

    var offset by mutableFloatStateOf(0f)

    private var showJob: Job? = null

    // The app bar hides along with the list, and comes back entirely as soon as it's scrolled up.
    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (!isEnabled) {
                return Offset.Zero
            }
            if (consumed.y < 0) {
                showJob?.cancel()
                showJob = null
                offset = (offset + consumed.y).coerceIn(-height, 0f)
            } else if (consumed.y > 0) {
                show()
            }
            return Offset.Zero
        }
    }

    fun show() {
        if (!isEnabled || offset >= 0 || showJob != null) {
            return
        }
        showJob = coroutineScope.launch {
            animate(offset, 0f) { value, _ -> offset = value }
            showJob = null
        }
    }
}

/**
 * The state of an app bar hiding along with the status bar area when [gridState] scrolls down, as
 * long as [isEnabled], for content scrolling behind it edge to edge.
 */
@Composable
fun rememberAppBarScrollState(gridState: LazyGridState, isEnabled: Boolean): AppBarScrollState {
    val coroutineScope = rememberCoroutineScope()
    val state = remember { AppBarScrollState(coroutineScope) }
    state.isEnabled = isEnabled
    LaunchedEffect(isEnabled) { state.offset = 0f }
    // Scrolling up with the fast scroller doesn't go through nested scrolling.
    LaunchedEffect(gridState, state) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val isScrolledUp = index < lastIndex || index == lastIndex && offset < lastOffset
                if (isScrolledUp && gridState.isScrollInProgress) {
                    state.show()
                }
                lastIndex = index
                lastOffset = offset
            }
    }
    // The status bar icons follow the content once the app bar is mostly gone from behind them.
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    val isAppBarBehindStatusBar by remember(state, statusBarHeight) {
        derivedStateOf { state.height + state.offset >= statusBarHeight / 2f }
    }
    if (!isAppBarBehindStatusBar) {
        StatusBarBackgroundEffect(MaterialTheme.colorScheme.background)
    }
    return state
}

/**
 * Moves the app bar up by the offset of [state], without changing its size, and shows it again
 * when focused.
 */
fun Modifier.hideOnScroll(state: AppBarScrollState): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = placeable.height.toFloat()
        if (state.height != height) {
            state.height = height
        }
        layout(placeable.width, placeable.height) {
            placeable.place(0, state.offset.roundToInt())
        }
    }
        .onFocusChanged { if (it.hasFocus) state.offset = 0f }

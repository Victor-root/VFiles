/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.theme.StatusBarBackgroundEffect
import kotlin.math.roundToInt

/**
 * How far an app bar hiding on scroll has moved up, from 0 to minus its height. It moves by as much
 * as the content scrolls, like the content pushed it.
 */
@Stable
class AppBarScrollState(
    private val coroutineScope: CoroutineScope,
    internal val statusBarContentLayer: GraphicsLayer
) {
    var isEnabled = true

    // The content behind the status bar is recorded while its color is needed.
    internal var isSamplingStatusBarContent = false

    internal var statusBarHeight = 0

    // Counts the recordings, so that the same content isn't sampled again.
    internal var statusBarContentVersion = 0

    // White or black like the content behind the status bar, once the app bar is gone from there.
    internal var statusBarContentColor by mutableStateOf<Color?>(null)

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
    val statusBarContentLayer = rememberGraphicsLayer()
    val state = remember { AppBarScrollState(coroutineScope, statusBarContentLayer) }
    state.isEnabled = isEnabled
    LaunchedEffect(isEnabled) { state.offset = 0f }
    // Scrolling up with the fast scroller doesn't go through nested scrolling, and its jumps may be
    // over before they are seen here, so any move up shows the app bar.
    LaunchedEffect(gridState, state) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val isScrolledUp = index < lastIndex || index == lastIndex && offset < lastOffset
                if (isScrolledUp) {
                    state.show()
                }
                lastIndex = index
                lastOffset = offset
            }
    }
    // The status bar icons follow the content once the app bar is mostly gone from behind them,
    // white over dark content and black over light content, like dark photos scrolling by.
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    SideEffect { state.statusBarHeight = statusBarHeight }
    // The content may not draw where it shows the background.
    val backgroundLuminance by
        rememberUpdatedState(MaterialTheme.colorScheme.background.luminance())
    val isAppBarBehindStatusBar by remember(state, statusBarHeight) {
        derivedStateOf { state.height + state.offset >= statusBarHeight / 2f }
    }
    LaunchedEffect(state, isAppBarBehindStatusBar) {
        if (isAppBarBehindStatusBar) {
            state.statusBarContentColor = null
            return@LaunchedEffect
        }
        state.isSamplingStatusBarContent = true
        var sampledVersion = -1
        try {
            while (true) {
                // Lets the content be drawn and recorded.
                withFrameNanos {}
                val version = state.statusBarContentVersion
                if (version != sampledVersion && state.statusBarContentLayer.size != IntSize.Zero) {
                    sampledVersion = version
                    val luminance = state.statusBarContentLayer.toImageBitmap()
                        .averageLuminance(backgroundLuminance)
                    if (BuildConfig.DEBUG) {
                        Log.d("AppBarScrollState", "Status bar content luminance: $luminance")
                    }
                    val isDark = when {
                        luminance < DARK_LUMINANCE_MAX -> true
                        luminance > LIGHT_LUMINANCE_MIN -> false
                        // Keeps the icons as they are in between, so that they don't flicker.
                        else -> state.statusBarContentColor?.let { it == Color.Black }
                            ?: (luminance < 0.5f)
                    }
                    state.statusBarContentColor = if (isDark) Color.Black else Color.White
                }
                delay(STATUS_BAR_SAMPLING_INTERVAL_MILLIS)
            }
        } finally {
            state.isSamplingStatusBarContent = false
        }
    }
    if (!isAppBarBehindStatusBar) {
        StatusBarBackgroundEffect(
            state.statusBarContentColor ?: MaterialTheme.colorScheme.background
        )
    }
    return state
}

private const val STATUS_BAR_SAMPLING_INTERVAL_MILLIS = 200L
private const val DARK_LUMINANCE_MAX = 0.4f
private const val LIGHT_LUMINANCE_MIN = 0.6f
// Every few pixels are enough to tell how dark the content is.
private const val SAMPLING_STEP = 8

private fun ImageBitmap.averageLuminance(backgroundLuminance: Float): Float {
    val bitmap = asAndroidBitmap().let {
        // The pixels of a hardware bitmap can't be read.
        if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
    }
    var luminanceSum = 0f
    var count = 0
    for (y in 0 until bitmap.height step SAMPLING_STEP) {
        for (x in 0 until bitmap.width step SAMPLING_STEP) {
            val color = Color(bitmap.getPixel(x, y))
            luminanceSum +=
                color.luminance() * color.alpha + backgroundLuminance * (1 - color.alpha)
            ++count
        }
    }
    return if (count > 0) luminanceSum / count else 1f
}

/**
 * Records what this content draws behind the status bar for [state], which makes the status bar
 * icons follow it once the app bar is hidden. The content must start at the top of the screen.
 */
fun Modifier.sampleStatusBarContent(state: AppBarScrollState): Modifier =
    drawWithContent {
        drawContent()
        if (state.isSamplingStatusBarContent && state.statusBarHeight > 0) {
            state.statusBarContentLayer.record(
                size = IntSize(size.width.roundToInt(), state.statusBarHeight)
            ) { this@drawWithContent.drawContent() }
            ++state.statusBarContentVersion
        }
    }

/** An app bar hiding on scroll with [state]. */
@Composable
fun HideOnScrollTopBar(state: AppBarScrollState, content: @Composable () -> Unit) {
    Box(modifier = Modifier.hideOnScroll(state)) { content() }
}

/**
 * Moves the app bar up by the offset of [state], without changing its size, and shows it again
 * when focused.
 */
private fun Modifier.hideOnScroll(state: AppBarScrollState): Modifier =
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

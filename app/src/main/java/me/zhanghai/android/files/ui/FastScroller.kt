/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zhanghai.android.files.util.PerfLog
import kotlin.math.roundToInt

/** What a fast scroller needs to know about a scrollable container. */
interface FastScrollable {
    /** How far the content is scrolled, from 0 to 1. */
    val scrollFraction: Float

    /** Whether the content is longer than the viewport, so that there is something to scroll. */
    val canScroll: Boolean

    val isScrollInProgress: Boolean

    suspend fun scrollToFraction(fraction: Float)
}

fun ScrollState.asFastScrollable(): FastScrollable =
    object : FastScrollable {
        override val scrollFraction: Float
            get() = if (maxValue > 0) value.toFloat() / maxValue else 0f

        override val canScroll: Boolean
            get() = maxValue > 0

        override val isScrollInProgress: Boolean
            get() = this@asFastScrollable.isScrollInProgress

        override suspend fun scrollToFraction(fraction: Float) {
            scrollTo((fraction * maxValue).roundToInt())
        }
    }

// The items are assumed to be the same height, so that the scroll position is known in pixels and
// the thumb moves smoothly instead of jumping from row to row.
fun LazyGridState.asFastScrollable(): FastScrollable =
    object : FastScrollable {
        private val columnCount: Int
            get() = layoutInfo.maxSpan.coerceAtLeast(1)

        private val rowHeight: Int
            get() = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0

        private val maxScrollOffset: Int
            get() {
                val layoutInfo = layoutInfo
                val rowCount = (layoutInfo.totalItemsCount + columnCount - 1) / columnCount
                return layoutInfo.beforeContentPadding + rowCount * rowHeight +
                    layoutInfo.afterContentPadding - layoutInfo.viewportSize.height
            }

        override val scrollFraction: Float
            get() {
                val maxScrollOffset = maxScrollOffset
                if (maxScrollOffset <= 0) {
                    return 0f
                }
                val scrollOffset =
                    firstVisibleItemIndex / columnCount * rowHeight + firstVisibleItemScrollOffset
                return (scrollOffset.toFloat() / maxScrollOffset).coerceIn(0f, 1f)
            }

        override val canScroll: Boolean
            get() = canScrollForward || canScrollBackward

        override val isScrollInProgress: Boolean
            get() = this@asFastScrollable.isScrollInProgress

        override suspend fun scrollToFraction(fraction: Float) {
            val rowHeight = rowHeight
            if (rowHeight <= 0) {
                return
            }
            val scrollOffset = (fraction * maxScrollOffset.coerceAtLeast(0)).roundToInt()
            scrollToItem(
                scrollOffset / rowHeight * columnCount, scrollOffset % rowHeight
            )
        }
    }

private val THUMB_WIDTH = 8.dp
private val THUMB_HEIGHT = 48.dp
private val TOUCH_WIDTH = 48.dp
private const val HIDE_DELAY_MILLIS = 1500L

/**
 * A draggable thumb along the end edge of [scrollable], shown while it scrolls or the thumb is
 * dragged. Place it in a Box over the scrollable content, filling it. [popupText] gives the text
 * of a bubble shown next to the thumb while it's dragged, like the first letter of the names.
 */
@Composable
fun FastScroller(
    scrollable: FastScrollable,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    popupText: (() -> String)? = null
) {
    PerfLog.Recomposed("FastScroller")
    if (!scrollable.canScroll) {
        return
    }
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    val isActive = scrollable.isScrollInProgress || isDragging
    LaunchedEffect(isActive) {
        if (isActive) {
            isVisible = true
        } else {
            delay(HIDE_DELAY_MILLIS)
            isVisible = false
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .padding(contentPadding)
            .fillMaxHeight()
    ) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { (maxHeight - THUMB_HEIGHT).toPx() }.coerceAtLeast(1f)
        if (popupText != null) {
            // Moved as a whole, since the fade clips what's drawn outside of its bounds.
            AnimatedVisibility(
                visible = isDragging,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .layout { measurable, constraints ->
                        // The pointed corner of the bubble faces the middle of the thumb.
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            val thumbCenterY = scrollable.scrollFraction * trackHeightPx +
                                THUMB_HEIGHT.toPx() / 2
                            val y = (thumbCenterY - placeable.height).coerceAtLeast(0f)
                            placeable.place(0, y.roundToInt())
                        }
                    },
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FastScrollerPopup(
                    text = popupText(),
                    modifier = Modifier.padding(end = TOUCH_WIDTH)
                )
            }
        }
        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (scrollable.scrollFraction * trackHeightPx).roundToInt()) },
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(TOUCH_WIDTH, THUMB_HEIGHT)
                    .pointerInput(scrollable, trackHeightPx) {
                        var fraction = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                fraction = scrollable.scrollFraction
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                fraction = (fraction + dragAmount / trackHeightPx).coerceIn(0f, 1f)
                                val targetFraction = fraction
                                coroutineScope.launch {
                                    scrollable.scrollToFraction(targetFraction)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .width(THUMB_WIDTH)
                        .fillMaxHeight()
                        .background(
                            if (isDragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            RoundedCornerShape(THUMB_WIDTH / 2)
                        )
                )
            }
        }
    }
}

private val POPUP_SIZE = 64.dp

@Composable
private fun FastScrollerPopup(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(POPUP_SIZE, POPUP_SIZE)
            .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(
                    topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 0,
                    bottomStartPercent = 50
                )
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

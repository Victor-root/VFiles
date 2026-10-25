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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

fun LazyListState.asFastScrollable(): FastScrollable =
    object : FastScrollable {
        override val scrollFraction: Float
            get() {
                val layoutInfo = layoutInfo
                val lastIndex = layoutInfo.totalItemsCount - layoutInfo.visibleItemsInfo.size
                if (lastIndex <= 0) {
                    return 0f
                }
                val firstItemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                return ((firstVisibleItemIndex + firstVisibleItemScrollOffset.toFloat() /
                    firstItemSize) / lastIndex).coerceIn(0f, 1f)
            }

        override val canScroll: Boolean
            get() = canScrollForward || canScrollBackward

        override val isScrollInProgress: Boolean
            get() = this@asFastScrollable.isScrollInProgress

        override suspend fun scrollToFraction(fraction: Float) {
            val layoutInfo = layoutInfo
            val lastIndex = layoutInfo.totalItemsCount - layoutInfo.visibleItemsInfo.size
            scrollToItem((fraction * lastIndex.coerceAtLeast(0)).roundToInt())
        }
    }

fun LazyGridState.asFastScrollable(): FastScrollable =
    object : FastScrollable {
        override val scrollFraction: Float
            get() {
                val layoutInfo = layoutInfo
                val lastIndex = layoutInfo.totalItemsCount - layoutInfo.visibleItemsInfo.size
                if (lastIndex <= 0) {
                    return 0f
                }
                return (firstVisibleItemIndex.toFloat() / lastIndex).coerceIn(0f, 1f)
            }

        override val canScroll: Boolean
            get() = canScrollForward || canScrollBackward

        override val isScrollInProgress: Boolean
            get() = this@asFastScrollable.isScrollInProgress

        override suspend fun scrollToFraction(fraction: Float) {
            val layoutInfo = layoutInfo
            val lastIndex = layoutInfo.totalItemsCount - layoutInfo.visibleItemsInfo.size
            scrollToItem((fraction * lastIndex.coerceAtLeast(0)).roundToInt())
        }
    }

private val THUMB_WIDTH = 8.dp
private val THUMB_HEIGHT = 48.dp
private val TOUCH_WIDTH = 48.dp
private const val HIDE_DELAY_MILLIS = 1500L

/**
 * A draggable thumb along the end edge of [scrollable], shown while it scrolls or the thumb is
 * dragged. Place it in a Box over the scrollable content, filling it.
 */
@Composable
fun FastScroller(
    scrollable: FastScrollable,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
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
        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(0, (scrollable.scrollFraction * trackHeightPx).roundToInt())
                    }
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

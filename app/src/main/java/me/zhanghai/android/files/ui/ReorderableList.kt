/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Lets the items of a lazy list be reordered by dragging their handle. Items move as soon as the
 * dragged one passes their middle, through [onMove] which must update the list.
 */
@Stable
class ReorderableListState internal constructor(
    val lazyListState: LazyListState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var draggedItemKey by mutableStateOf<Any?>(null)
        private set

    private var draggedItemOffset by mutableFloatStateOf(0f)

    internal var scrollSpeed by mutableFloatStateOf(0f)
        private set

    internal fun onDragStart(key: Any) {
        draggedItemKey = key
        draggedItemOffset = 0f
    }

    internal fun onDrag(delta: Float) {
        draggedItemOffset += delta
        val draggedItem = draggedItemInfo ?: return
        val draggedTop = draggedItem.offset + draggedItemOffset
        val draggedBottom = draggedTop + draggedItem.size
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find {
            it.key != draggedItem.key &&
                if (it.index > draggedItem.index) {
                    draggedBottom > it.offset + it.size / 2
                } else {
                    draggedTop < it.offset + it.size / 2
                }
        }
        if (targetItem != null) {
            // The dragged item jumps to its new position, compensate so that it stays under the
            // finger.
            draggedItemOffset += draggedItem.offset - targetItem.offset
            onMove(draggedItem.index, targetItem.index)
        }
        val layoutInfo = lazyListState.layoutInfo
        scrollSpeed = when {
            draggedTop < layoutInfo.viewportStartOffset ->
                draggedTop - layoutInfo.viewportStartOffset
            draggedBottom > layoutInfo.viewportEndOffset ->
                draggedBottom - layoutInfo.viewportEndOffset
            else -> 0f
        } / 4
    }

    internal fun onDragEnd() {
        draggedItemKey = null
        draggedItemOffset = 0f
        scrollSpeed = 0f
    }

    internal fun onScrolled(delta: Float) {
        // Keep the dragged item under the finger while the list scrolls.
        onDrag(delta)
    }

    internal fun offsetOf(key: Any): Float = if (key == draggedItemKey) draggedItemOffset else 0f

    private val draggedItemInfo: LazyListItemInfo?
        get() = lazyListState.layoutInfo.visibleItemsInfo.find { it.key == draggedItemKey }
}

@Composable
fun rememberReorderableListState(
    lazyListState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderableListState {
    val currentOnMove by rememberUpdatedState(onMove)
    val state = remember(lazyListState) {
        ReorderableListState(lazyListState) { from, to -> currentOnMove(from, to) }
    }
    // Scrolls when the dragged item reaches an edge of the list.
    val isDragging = state.draggedItemKey != null
    LaunchedEffect(state, isDragging) {
        while (isDragging) {
            withFrameNanos {}
            val scrollSpeed = state.scrollSpeed
            if (scrollSpeed != 0f) {
                val scrolled = lazyListState.scrollBy(scrollSpeed)
                state.onScrolled(scrolled)
            }
        }
    }
    return state
}

/** Applies the drag offset and elevation to the item with [key]. */
fun Modifier.reorderableItem(state: ReorderableListState, key: Any): Modifier {
    val isDragged = state.draggedItemKey == key
    return then(
        if (isDragged) {
            Modifier
                .zIndex(1f)
                .graphicsLayer { translationY = state.offsetOf(key) }
                .shadow(8.dp)
        } else {
            Modifier
        }
    )
}

/** Makes this element the handle that drags the item with [key]. */
fun Modifier.reorderDragHandle(state: ReorderableListState, key: Any): Modifier =
    pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.onDragStart(key) },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            }
        )
    }

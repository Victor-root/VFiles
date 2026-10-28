/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.zhanghai.android.files.R

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

    // An item picked up with a D-pad, which moves without scrolling frame by frame.
    internal var isMovingWithKeys by mutableStateOf(false)
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
            // finger. Moving down, it ends where the target ended, which differs from where the
            // target started when they aren't as tall.
            val newOffset = if (targetItem.index > draggedItem.index) {
                targetItem.offset + targetItem.size - draggedItem.size
            } else {
                targetItem.offset
            }
            draggedItemOffset += draggedItem.offset - newOffset
            moveItem(draggedItem.index, targetItem.index)
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

    // A D-pad picks the item up, moves it by one with each key, and puts it down.
    internal fun onKeyEvent(event: KeyEvent, key: Any): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                if (draggedItemKey == key) {
                    onDragEnd()
                } else {
                    onDragStart(key)
                    isMovingWithKeys = true
                }
            Key.DirectionUp, Key.DirectionDown -> {
                if (draggedItemKey != key) {
                    return false
                }
                val layoutInfo = lazyListState.layoutInfo
                val index = draggedItemInfo?.index ?: return true
                val targetIndex = index + if (event.key == Key.DirectionUp) -1 else 1
                if (targetIndex in 0..<layoutInfo.totalItemsCount) {
                    moveItem(index, targetIndex)
                    // Keeps the moved item in view.
                    val firstVisibleIndex = layoutInfo.visibleItemsInfo.first().index
                    val lastVisibleIndex = layoutInfo.visibleItemsInfo.last().index
                    if (targetIndex <= firstVisibleIndex) {
                        lazyListState.requestScrollToItem(targetIndex)
                    } else if (targetIndex >= lastVisibleIndex) {
                        lazyListState.requestScrollToItem(
                            firstVisibleIndex + targetIndex - lastVisibleIndex + 1
                        )
                    }
                }
            }
            else -> return false
        }
        return true
    }

    // A lazy list keeps its first visible item in place, so it would scroll along with that item
    // when it moves, taking the dragged item away from the finger.
    private fun moveItem(fromIndex: Int, toIndex: Int) {
        val firstVisibleIndex = lazyListState.firstVisibleItemIndex
        if (fromIndex == firstVisibleIndex || toIndex == firstVisibleIndex) {
            lazyListState.requestScrollToItem(
                firstVisibleIndex, lazyListState.firstVisibleItemScrollOffset
            )
        }
        onMove(fromIndex, toIndex)
    }

    internal fun onDragEnd() {
        draggedItemKey = null
        isMovingWithKeys = false
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
    // Scrolls when the item dragged with a pointer reaches an edge of the list.
    val isDragging = state.draggedItemKey != null && !state.isMovingWithKeys
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

/**
 * The handle that drags the item with [key], or moves it with a D-pad: the center key picks the item
 * up or puts it down, and up and down move it meanwhile.
 */
@Composable
fun ReorderDragHandle(
    state: ReorderableListState,
    key: Any,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    Icon(
        painter = painterResource(R.drawable.drag_handle_icon_white_24dp),
        contentDescription = stringResource(R.string.reorder_drag_handle),
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .reorderDragHandle(state, key)
            .onFocusChanged {
                // Puts the item down when the D-pad leaves it.
                if (!it.isFocused && state.draggedItemKey == key) {
                    state.onDragEnd()
                }
            }
            .onKeyEvent { state.onKeyEvent(it, key) }
            .focusable(interactionSource = interactionSource)
            .indication(interactionSource, ripple())
            .padding(12.dp),
        tint = tint
    )
}

private fun Modifier.reorderDragHandle(state: ReorderableListState, key: Any): Modifier =
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

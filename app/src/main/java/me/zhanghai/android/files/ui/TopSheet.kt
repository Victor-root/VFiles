/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MAX_WIDTH = 640.dp
private val SHAPE = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

// The part of its height to drag a sheet up to close it, unless flung.
private const val DISMISS_FRACTION = 0.25f
private const val DISMISS_VELOCITY = 1000f

/**
 * Like a bottom sheet but coming from the top of the screen, which closes when dragged up, when
 * touching outside of it or going back.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TopSheet(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = isOpen
    if (!isOpen && visibleState.isIdle && !visibleState.currentState) {
        return
    }
    // A dialog doesn't get the insets of the screen on all versions of Android.
    val contentPadding = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        .asPaddingValues()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val containerColor = BottomSheetDefaults.ContainerColor
        val view = LocalView.current
        SideEffect {
            // The scrim fades along with the sheet, and the sheet goes behind the status bar.
            val window = (view.parent as DialogWindowProvider).window
            window.setDimAmount(0f)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                containerColor.luminance() > 0.5f
        }
        val transition = rememberTransition(visibleState)
        Box(modifier = Modifier.fillMaxSize()) {
            transition.AnimatedVisibility(
                visible = { it },
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BottomSheetDefaults.ScrimColor)
                        .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
                )
            }
            transition.AnimatedVisibility(
                visible = { it },
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it }
            ) {
                DraggableSheet(contentPadding, onDismissRequest, content)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraggableSheet(
    contentPadding: PaddingValues,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var height by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val draggableState = rememberDraggableState {
        dragOffset = (dragOffset + it).coerceAtMost(0f)
    }
    val settle = { velocity: Float ->
        if (dragOffset < -height * DISMISS_FRACTION || velocity < -DISMISS_VELOCITY) {
            onDismissRequest()
        } else {
            coroutineScope.launch {
                animate(dragOffset, 0f) { value, _ -> dragOffset = value }
            }
        }
        Unit
    }
    val inputModeManager = LocalInputModeManager.current
    // The content scrolls, so the drags it doesn't use are given to the sheet, unlike the scrolling
    // that brings what is focused with a D-pad into view.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dragOffset >= 0f || available.y <= 0f) {
                    return Offset.Zero
                }
                val consumed = available.y.coerceAtMost(-dragOffset)
                dragOffset += consumed
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f ||
                    inputModeManager.inputMode != InputMode.Touch
                ) {
                    return Offset.Zero
                }
                dragOffset += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffset >= 0f) {
                    return Velocity.Zero
                }
                settle(available.y)
                return available
            }
        }
    }
    Surface(
        modifier = Modifier
            .widthIn(max = MAX_WIDTH)
            .fillMaxWidth()
            .onSizeChanged { height = it.height }
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { settle(it) }
            )
            .nestedScroll(nestedScrollConnection),
        shape = SHAPE,
        color = BottomSheetDefaults.ContainerColor,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                content = content
            )
            BottomSheetDefaults.DragHandle()
        }
    }
}

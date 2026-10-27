/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R

private const val SCRIM_ALPHA = 0.32f
private const val DISABLED_ALPHA = 0.38f

/**
 * A sheet of actions sliding up from the bottom of the screen, with an optional [title]. Its
 * content gets a function closing it.
 */
@Composable
fun ActionBottomSheet(
    onDismissRequest: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
    }
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        ActionSheetContent(
            title = title,
            modifier = Modifier.padding(bottom = 16.dp),
            content = { content(dismiss) }
        )
    }
}

/**
 * A sheet of actions sliding down from the top of the screen, for the menus of the app bar. Its
 * content gets a function closing it.
 */
@Composable
fun ActionTopSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    // Removed once its closing animation is done.
    if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
        LaunchedEffect(visibleState) { onDismissRequest() }
    }
    val dismiss = { visibleState.targetState = false }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )
    ) {
        // The scrim below fades in with the sheet instead of the dim of the dialog window.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) { window?.setDimAmount(0f) }
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = visibleState, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                        .clickable(interactionSource = null, indication = null, onClick = dismiss)
                )
            }
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it }
            ) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    color = BottomSheetDefaults.ContainerColor,
                    tonalElevation = BottomSheetDefaults.Elevation
                ) {
                    Column(
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.systemBars
                                .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ActionSheetContent(
                            title = null,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(top = 8.dp),
                            content = { content(dismiss) }
                        )
                        BottomSheetDefaults.DragHandle()
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionSheetContent(
    title: String?,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // On a D-pad, the first action is focused first.
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .focusRequester(focusRequester)
            .focusGroup()
    ) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        }
        content()
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** An action in a sheet, with its icon if any and an optional trailing content. */
@Composable
fun SheetItem(
    @DrawableRes iconRes: Int?,
    title: String,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailingContent()
        }
    }
}

/** A line between the groups of actions of a sheet. */
@Composable
fun SheetDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

/**
 * An overflow button in an app bar, opening a top sheet whose content gets a function closing it.
 */
@Composable
fun OverflowMenu(content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
    var isMenuShown by remember { mutableStateOf(false) }
    IconButton(onClick = { isMenuShown = true }) {
        Icon(
            painter = painterResource(R.drawable.more_vertical_icon_white_24dp),
            contentDescription = stringResource(R.string.more_options)
        )
    }
    if (isMenuShown) {
        ActionTopSheet(onDismissRequest = { isMenuShown = false }, content = content)
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import me.zhanghai.android.files.R
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.LocalAppColors

/**
 * A screen with an app bar, following the edge-to-edge setting: when it's on, the content scrolls
 * behind the navigation bar and has to apply the bottom inset of the padding itself; otherwise the
 * content stops above the navigation bar.
 */
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = { NavigateUpButton() },
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    content: @Composable (PaddingValues) -> Unit
) {
    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = scrollBehavior
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = floatingActionButton,
        snackbarHostState = snackbarHostState,
        content = content
    )
}

/**
 * A screen with its own [topBar], following the edge-to-edge setting like the one above. The
 * content is placed below the top bar, unless [isContentBehindTopBar], when it gets the height of
 * the top bar in the top of its padding instead.
 */
@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    isContentBehindTopBar: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val isEdgeToEdge by Settings.EDGE_TO_EDGE.flow.collectAsState()
    Scaffold(
        modifier = modifier.then(
            if (isEdgeToEdge) {
                Modifier
            } else {
                val navigationBars = WindowInsets.navigationBars
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                // The navigation bar has the color of the app bar.
                Modifier
                    .background(LocalAppColors.current.appBarContainer)
                    .windowInsetsPadding(navigationBars)
                    .consumeWindowInsets(navigationBars)
            }
        ),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton
    ) { contentPadding ->
        if (isContentBehindTopBar) {
            content(contentPadding)
        } else {
            // A scrolling container behind the top bar keeps a D-pad from moving the focus down
            // from the top bar to the content, and the top bar hides that part anyway.
            val layoutDirection = LocalLayoutDirection.current
            // On a D-pad, the content is focused first rather than the app bar.
            val contentFocusRequester = remember { FocusRequester() }
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(contentFocusRequester) {
                if (inputModeManager.inputMode == InputMode.Keyboard) {
                    // Lets lazy lists lay out their first items.
                    withFrameNanos {}
                    contentFocusRequester.requestFocus()
                }
            }
            Box(
                modifier = Modifier
                    .padding(top = contentPadding.calculateTopPadding())
                    .focusRequester(contentFocusRequester)
                    // Focuses the item focused before again, like when coming back from another
                    // screen.
                    .focusRestorer()
            ) {
                content(
                    PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding()
                    )
                )
            }
        }
    }
}

@Composable
fun AppTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = { NavigateUpButton() },
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = appTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            if (subtitle != null) {
                Column {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun appTopAppBarColors(): TopAppBarColors {
    val appColors = LocalAppColors.current
    return TopAppBarDefaults.topAppBarColors(
        containerColor = appColors.appBarContainer,
        scrolledContainerColor = appColors.appBarContainer,
        navigationIconContentColor = appColors.onAppBar,
        titleContentColor = appColors.onAppBar,
        actionIconContentColor = appColors.onAppBar,
        subtitleContentColor = appColors.onAppBar
    )
}

@Composable
fun NavigateUpButton() {
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
        Icon(
            painter = painterResource(R.drawable.arrow_back_icon_white_24dp),
            contentDescription = stringResource(R.string.navigate_up)
        )
    }
}

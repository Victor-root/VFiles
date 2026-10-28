/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java8.nio.file.Path
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.theme.LocalAppColors
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe

/**
 * The bookmarks, standard folders, storages and app menu of the file list. [subtitleKey] changes
 * whenever the free space shown for storages may have changed, so that it's read again. Each new
 * [focusCheckedItemRequest] above 0 moves the D-pad focus to the item of the current folder, or the
 * first item, like when the drawer opens.
 */
@Composable
fun NavigationDrawerContent(
    currentPath: Path,
    onNavigateTo: (Path) -> Unit,
    onNavigateToRoot: (Path) -> Unit,
    onCloseDrawer: () -> Unit,
    subtitleKey: Any?,
    focusCheckedItemRequest: Int,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val items by NavigationItemList.flow.collectAsState()
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)
    val currentOnNavigateToRoot by rememberUpdatedState(onNavigateToRoot)
    val currentOnCloseDrawer by rememberUpdatedState(onCloseDrawer)
    val currentPathState = rememberUpdatedState(currentPath)
    // Kept from a folder to the next one, so that only the items checked or unchecked recompose.
    val listener = remember(context, navigator) {
        object : NavigationItem.Listener {
            override val currentPath: Path
                get() = currentPathState.value

            override fun navigateTo(path: Path) {
                currentOnNavigateTo(path)
            }

            override fun navigateToRoot(path: Path) {
                currentOnNavigateToRoot(path)
            }

            override fun launchIntent(intent: Intent) {
                context.startActivitySafe(intent)
            }

            override fun navigate(destination: Destination) {
                navigator.navigate(destination)
            }

            override fun showToast(textRes: Int) {
                context.showToast(textRes, Toast.LENGTH_LONG)
            }

            override fun closeNavigationDrawer() {
                currentOnCloseDrawer()
            }
        }
    }
    val focusedIndex = items.indexOfFirst { it != null && it.isChecked(listener) }
        .takeIf { it != -1 } ?: items.indexOfFirst { it != null && !it.isHeader }
    val checkedItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(focusCheckedItemRequest) {
        if (focusCheckedItemRequest == 0 || focusedIndex == -1) {
            return@LaunchedEffect
        }
        listState.scrollToItem(focusedIndex)
        // Lets the item be composed if it was scrolled into view.
        withFrameNanos {}
        checkedItemFocusRequester.requestFocus()
    }
    LazyColumn(
        // Focuses the item focused before again, like when coming back to the drawer.
        modifier = modifier.focusRestorer(),
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 8.dp
        )
    ) {
        itemsIndexed(
            items,
            key = { index, item -> item?.id ?: "divider$index" },
            contentType = { _, item -> if (item != null) "item" else "divider" }
        ) { index, item ->
            if (item == null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            } else {
                NavigationItemRow(
                    item = item,
                    isChecked = item.isChecked(listener),
                    listener = listener,
                    subtitleKey = subtitleKey,
                    modifier = if (index == focusedIndex) {
                        Modifier.focusRequester(checkedItemFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavigationItemRow(
    item: NavigationItem,
    isChecked: Boolean,
    listener: NavigationItem.Listener,
    subtitleKey: Any?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = remember(item, context) { item.getTitle(context) }
    // Reads the free space for storages.
    val subtitle = remember(item, context, subtitleKey) { item.getSubtitle(context) }
    val colorScheme = MaterialTheme.colorScheme
    val appColors = LocalAppColors.current
    val contentColor = when {
        isChecked -> appColors.onAppBar
        item.isHeader -> colorScheme.onSurfaceVariant
        else -> colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .then(
                if (item.isHeader) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        onClick = { item.onClick(listener) },
                        onLongClick = { item.onLongClick(listener) }
                    )
                }
            ),
        shape = CircleShape,
        // The current item has the color of the app bar.
        color = if (isChecked) appColors.appBarContainer else Color.Transparent,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(
                // Nested rows, like the partitions of a drive, are indented.
                start = if (item.isIndented) 32.dp else 16.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                // On the app bar color, the icon takes the variant for its background.
                tint = item.iconColor?.getColor(
                    if (isChecked) appColors.onAppBar.luminance() > 0.5f else appColors.isDark
                )
                    ?: if (isChecked) contentColor else colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge
                )
                if (subtitle != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (isChecked) {
                            contentColor
                        } else {
                            colorScheme.onSurfaceVariant
                        }
                    ) {
                        Text(
                            text = subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

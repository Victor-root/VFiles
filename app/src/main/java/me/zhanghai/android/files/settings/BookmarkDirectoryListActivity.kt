/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.navigation.BookmarkDirectories
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.EditBookmarkDirectoryDialogActivity
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.ReorderableListState
import me.zhanghai.android.files.ui.rememberReorderableListState
import me.zhanghai.android.files.ui.reorderDragHandle
import me.zhanghai.android.files.ui.reorderableItem
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.observeAsStateCompat
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe

class BookmarkDirectoryListActivity : ComposeActivity() {
    @Composable
    override fun Content() {
        BookmarkDirectoryListScreen()
    }
}

@Composable
private fun BookmarkDirectoryListScreen() {
    val context = LocalContext.current
    val bookmarkDirectories by Settings.BOOKMARK_DIRECTORIES.observeAsStateCompat()
    val openPathLauncher =
        rememberLauncherForActivityResult(FileListActivity.OpenDirectoryContract()) {
            if (it != null) {
                BookmarkDirectories.add(BookmarkDirectory(null, it))
            }
        }
    AppScaffold(
        title = stringResource(R.string.settings_bookmark_directory_list_title),
        floatingActionButton = {
            FloatingActionButton(onClick = { openPathLauncher.launch(null) }) {
                Icon(
                    painter = painterResource(R.drawable.add_icon_white_24dp),
                    contentDescription =
                        stringResource(R.string.settings_bookmark_directory_list_add)
                )
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = bookmarkDirectories.isEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.settings_bookmark_directory_list_empty),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            val lazyListState = rememberLazyListState()
            val reorderableListState = rememberReorderableListState(lazyListState) { from, to ->
                BookmarkDirectories.move(from, to)
            }
            val layoutDirection = LocalLayoutDirection.current
            LazyColumn(
                state = lazyListState,
                // Leave room for the floating action button below the last item.
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding() + 88.dp
                )
            ) {
                items(bookmarkDirectories, key = { it.id }) {
                    BookmarkDirectoryItem(
                        bookmarkDirectory = it,
                        reorderableListState = reorderableListState,
                        onClick = {
                            context.startActivitySafe(
                                EditBookmarkDirectoryDialogActivity::class.createIntent()
                                    .putArgs(EditBookmarkDirectoryDialogActivity.Args(it))
                            )
                        },
                        modifier = if (it.id != reorderableListState.draggedItemKey) {
                            Modifier.animateItem()
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkDirectoryItem(
    bookmarkDirectory: BookmarkDirectory,
    reorderableListState: ReorderableListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val key = bookmarkDirectory.id
    Row(
        modifier = modifier
            .reorderableItem(reorderableListState, key)
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(
            painter = painterResource(R.drawable.directory_icon_white_24dp),
            contentDescription = null,
            modifier = Modifier.padding(start = 16.dp),
            tint = iconTint
        )
        Spacer(modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmarkDirectory.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = bookmarkDirectory.path.toUserFriendlyString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Icon(
            painter = painterResource(R.drawable.drag_handle_icon_white_24dp),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp, end = 4.dp)
                .size(48.dp)
                .reorderDragHandle(reorderableListState, key)
                .padding(12.dp),
            tint = iconTint
        )
    }
}

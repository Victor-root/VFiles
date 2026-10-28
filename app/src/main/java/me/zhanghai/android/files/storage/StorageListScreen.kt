/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.ReorderDragHandle
import me.zhanghai.android.files.ui.ReorderableListState
import me.zhanghai.android.files.ui.rememberReorderableListState
import me.zhanghai.android.files.ui.reorderableItem
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe

@Parcelize
data object StorageListDestination : Destination {
    @Composable
    override fun Content() {
        StorageListScreen()
    }
}

@Composable
internal fun StorageListScreen() {
    val context = LocalContext.current
    val storages by Settings.STORAGES.flow.collectAsState()
    AppScaffold(
        title = stringResource(R.string.storage_list_title),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    context.startActivitySafe(AddStorageDialogActivity::class.createIntent())
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.add_icon_white_24dp),
                    contentDescription = stringResource(R.string.storage_add_storage_title)
                )
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = storages.isEmpty(),
                modifier = Modifier
                    .padding(contentPadding)
                    .align(Alignment.Center),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = stringResource(R.string.storage_list_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            val lazyListState = rememberLazyListState()
            val reorderableListState = rememberReorderableListState(lazyListState) { from, to ->
                Storages.move(from, to)
            }
            val layoutDirection = LocalLayoutDirection.current
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                // Leaves room for the floating action button below the last item.
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding() + 88.dp
                )
            ) {
                items(storages, key = { it.id }) { storage ->
                    val isDragged = reorderableListState.draggedItemKey == storage.id
                    StorageItem(
                        storage = storage,
                        reorderableListState = reorderableListState,
                        onClick = { context.startActivitySafe(storage.createEditIntent()) },
                        // The dragged item follows the finger instead.
                        modifier = if (isDragged) Modifier else Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageItem(
    storage: Storage,
    reorderableListState: ReorderableListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = storage.getName(LocalContext.current),
                color = if (storage.isVisible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        modifier = modifier
            .reorderableItem(reorderableListState, storage.id)
            .clickable(onClick = onClick),
        supportingContent = {
            Text(text = storage.description, overflow = TextOverflow.Ellipsis, maxLines = 1)
        },
        leadingContent = {
            Icon(painter = painterResource(storage.iconRes), contentDescription = null)
        },
        trailingContent = {
            ReorderDragHandle(state = reorderableListState, key = storage.id)
        }
    )
}

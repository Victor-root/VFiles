/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.ui.ActionButton
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.AppTopAppBar
import me.zhanghai.android.files.ui.NavigateUpButton
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent

import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.supportsExternalStorageManager

/** The photos and videos of the device by date, and their albums, like the gallery of a phone. */
class GalleryActivity : AppActivity() {
    @Composable
    override fun Content() {
        val state by GalleryLiveData.observeAsState()
        GalleryScreen(state)
    }
}

private enum class GalleryTab {
    PHOTOS,
    ALBUMS
}

private val SelectedIdsSaver = listSaver<Set<Long>, Long>(
    save = { it.toList() },
    restore = { it.toSet() }
)

@Composable
private fun GalleryScreen(state: GalleryState?) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS) }
    var albumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedIds by rememberSaveable(stateSaver = SelectedIdsSaver) {
        mutableStateOf(emptySet())
    }
    // Kept while the albums are shown, like the gallery of a phone.
    val photosGridState = rememberLazyGridState()
    val currentAlbumId = albumId
    val album = currentAlbumId?.let { state?.getAlbum(it) }
    // An album whose media are all gone is closed.
    LaunchedEffect(state, currentAlbumId) {
        if (state != null && currentAlbumId != null && album == null) {
            albumId = null
        }
    }
    val media = when {
        album != null -> album.media
        tab == GalleryTab.PHOTOS -> state?.media.orEmpty()
        else -> emptyList()
    }
    val selectedMedia = media.filter { it.id in selectedIds }
    val clearSelection = { selectedIds = emptySet() }
    val deleteMedia = rememberGalleryMediaDeleter(onDeleted = clearSelection)
    val toggleSelection = { items: List<GalleryMedia> ->
        val ids = items.map { it.id }
        selectedIds = if (selectedIds.containsAll(ids)) selectedIds - ids else selectedIds + ids
    }
    // Declared in increasing priority.
    BackHandler(enabled = album != null) { albumId = null }
    BackHandler(enabled = selectedMedia.isNotEmpty(), onBack = clearSelection)

    AppScaffold(
        topBar = {
            if (selectedMedia.isNotEmpty()) {
                GallerySelectionAppBar(
                    count = selectedMedia.size,
                    onClose = clearSelection,
                    onShare = { context.shareGalleryMedia(selectedMedia) },
                    onDelete = { deleteMedia(selectedMedia) },
                    onSelectAll = { selectedIds = media.mapTo(mutableSetOf()) { it.id } }
                )
            } else {
                AppTopAppBar(
                    title = album?.name ?: stringResource(R.string.gallery_title),
                    navigationIcon = {
                        if (album != null) {
                            IconButton(onClick = { albumId = null }) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_back_icon_white_24dp),
                                    contentDescription = stringResource(R.string.navigate_up)
                                )
                            }
                        } else {
                            NavigateUpButton()
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (album == null) {
                GalleryNavigationBar(
                    tab = tab,
                    onTabChange = {
                        tab = it
                        clearSelection()
                    }
                )
            }
        }
    ) { contentPadding ->
        when {
            state == null ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.isAccessDenied -> GalleryAccessDenied()
            album != null || tab == GalleryTab.PHOTOS ->
                if (media.isEmpty()) {
                    GalleryEmpty()
                } else {
                    // Each album has its own scroll position.
                    key(currentAlbumId) {
                        GalleryMediaGrid(
                            media = media,
                            selectedIds = selectedIds,
                            onMediaClick = {
                                if (selectedIds.isNotEmpty()) {
                                    toggleSelection(listOf(it))
                                } else {
                                    context.openGalleryViewer(album?.id, it)
                                }
                            },
                            onMediaLongClick = { toggleSelection(listOf(it)) },
                            onSectionClick = toggleSelection,
                            gridState = if (album != null) {
                                rememberLazyGridState()
                            } else {
                                photosGridState
                            },
                            contentPadding = contentPadding
                        )
                    }
                }
            else ->
                GalleryAlbumGrid(
                    albums = state.albums,
                    onAlbumClick = { albumId = it.id },
                    contentPadding = contentPadding
                )
        }
    }
}

private fun Context.openGalleryViewer(albumId: Long?, media: GalleryMedia) {
    startActivitySafe(
        GalleryViewerActivity::class.createIntent()
            .putArgs(GalleryViewerActivity.Args(albumId, media.id))
    )
}

@Composable
private fun GallerySelectionAppBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit
) {
    AppTopAppBar(
        title = stringResource(R.string.gallery_selection_title_format, count),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.close_icon_white_24dp),
                    contentDescription = stringResource(R.string.close)
                )
            }
        },
        actions = {
            ActionButton(
                R.drawable.share_icon_control_normal_24dp, stringResource(R.string.share), onShare
            )
            ActionButton(
                R.drawable.delete_icon_control_normal_24dp, stringResource(R.string.delete),
                onDelete
            )
            ActionButton(
                R.drawable.select_all_icon_control_normal_24dp, stringResource(R.string.select_all),
                onSelectAll
            )
        }
    )
}

@Composable
private fun GalleryNavigationBar(tab: GalleryTab, onTabChange: (GalleryTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = tab == GalleryTab.PHOTOS,
            onClick = { onTabChange(GalleryTab.PHOTOS) },
            icon = { Icon(painterResource(R.drawable.image_icon_white_24dp), null) },
            label = { Text(stringResource(R.string.gallery_photos)) }
        )
        NavigationBarItem(
            selected = tab == GalleryTab.ALBUMS,
            onClick = { onTabChange(GalleryTab.ALBUMS) },
            icon = { Icon(painterResource(R.drawable.gallery_icon_white_24dp), null) },
            label = { Text(stringResource(R.string.gallery_albums)) }
        )
    }
}

private val ALBUM_MIN_WIDTH = 150.dp
private val ALBUM_SHAPE = RoundedCornerShape(16.dp)

@Composable
private fun GalleryAlbumGrid(
    albums: List<GalleryAlbum>,
    onAlbumClick: (GalleryAlbum) -> Unit,
    contentPadding: PaddingValues
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(ALBUM_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            GalleryAlbumItem(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun GalleryAlbumItem(album: GalleryAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(ALBUM_SHAPE)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        AsyncImage(
            model = album.cover,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ALBUM_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentScale = ContentScale.Crop
        )
        Text(
            text = album.name,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = pluralStringResource(
                R.plurals.gallery_album_item_count_format, album.media.size, album.media.size
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GalleryEmpty() {
    GalleryMessage(stringResource(R.string.gallery_empty))
}

@Composable
private fun GalleryAccessDenied() {
    val context = LocalContext.current
    // Coming back from the settings where the access is given.
    LifecycleResumeEffect(Unit) {
        GalleryLiveData.reload()
        onPauseOrDispose {}
    }
    GalleryMessage(stringResource(R.string.gallery_access_denied)) {
        Button(
            onClick = { context.startActivitySafe(context.createAccessSettingsIntent()) },
            modifier = Modifier.padding(top = 16.dp)
        ) { Text(stringResource(R.string.gallery_allow_access)) }
    }
}

private fun Context.createAccessSettingsIntent(): Intent =
    if (Environment::class.supportsExternalStorageManager()) {
        Environment::class.createManageAppAllFilesAccessPermissionIntent(packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }

@Composable
private fun GalleryMessage(text: String, content: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.gallery_icon_white_24dp),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        content()
    }
}

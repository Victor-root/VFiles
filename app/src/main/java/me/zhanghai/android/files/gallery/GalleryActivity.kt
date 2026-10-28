/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
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
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.LocalAppColors
import me.zhanghai.android.files.ui.ActionButton
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.AppTopAppBar
import me.zhanghai.android.files.ui.HideOnScrollTopBar
import me.zhanghai.android.files.ui.NavigateUpButton
import me.zhanghai.android.files.ui.rememberAppBarScrollState
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.isTelevision
import me.zhanghai.android.files.util.observeAsStateCompat
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
    // Kept while another tab or an album is shown, like the gallery of a phone, while each album
    // has its own.
    val photosGridState = rememberLazyGridState()
    val albumsGridState = rememberLazyGridState()
    val albumGridState = key(albumId) { rememberLazyGridState() }
    // The album to focus again once it's closed, on a D-pad.
    var albumIdToFocus by remember { mutableStateOf<Long?>(null) }
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
    val closeAlbum = {
        albumIdToFocus = albumId
        albumId = null
    }
    val onTabChange = { newTab: GalleryTab ->
        tab = newTab
        clearSelection()
    }
    val isTelevision = remember(context) { context.isTelevision }
    // Edge to edge, the app bar hides when scrolling down like in the file list, except while
    // selecting, or on a TV, where the media focused with the D-pad must never be behind it.
    val isEdgeToEdge by Settings.EDGE_TO_EDGE.observeAsStateCompat()
    val isContentBehindTopBar = isEdgeToEdge && !isTelevision
    val gridState = when {
        album != null -> albumGridState
        tab == GalleryTab.PHOTOS -> photosGridState
        else -> albumsGridState
    }
    val appBarState = rememberAppBarScrollState(
        gridState, isEnabled = isContentBehindTopBar && selectedMedia.isEmpty()
    )
    LaunchedEffect(tab, currentAlbumId) { appBarState.offset = 0f }
    // On a D-pad, the media are focused rather than the tabs once they are shown, and again when
    // the focused one is gone, like when opening or closing an album.
    val contentFocusRequester = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(state == null, state?.isAccessDenied, currentAlbumId) {
        if (state != null && inputModeManager.inputMode == InputMode.Keyboard) {
            // Lets the grids lay out their first items, and an album focus itself.
            withFrameNanos {}
            if (!contentHasFocus) {
                contentFocusRequester.requestFocus()
            }
        }
    }
    // Declared in increasing priority.
    BackHandler(enabled = album != null, onBack = closeAlbum)
    BackHandler(enabled = selectedMedia.isNotEmpty(), onBack = clearSelection)

    AppScaffold(
        modifier = Modifier.nestedScroll(appBarState.nestedScrollConnection),
        isContentBehindTopBar = true,
        topBar = {
            HideOnScrollTopBar(appBarState) {
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
                                IconButton(onClick = closeAlbum) {
                                    Icon(
                                        painter = painterResource(
                                            R.drawable.arrow_back_icon_white_24dp
                                        ),
                                        contentDescription = stringResource(R.string.navigate_up)
                                    )
                                }
                            } else {
                                NavigateUpButton()
                            }
                        },
                        actions = {
                            if (album == null) {
                                GalleryTabSwitch(tab = tab, onTabChange = onTabChange)
                            }
                        }
                    )
                }
            }
        }
    ) { scaffoldPadding ->
        val appBarHeight = scaffoldPadding.calculateTopPadding()
        val layoutDirection = LocalLayoutDirection.current
        // On a TV, the media keep away from the edges of the screen, which may be cut, and leave
        // room for growing when focused.
        val televisionMargin = if (isTelevision) TELEVISION_MARGIN else 0.dp
        val contentPadding = PaddingValues(
            start = scaffoldPadding.calculateStartPadding(layoutDirection) + televisionMargin,
            top = if (isContentBehindTopBar) appBarHeight else 0.dp,
            end = scaffoldPadding.calculateEndPadding(layoutDirection) + televisionMargin,
            bottom = scaffoldPadding.calculateBottomPadding() + televisionMargin
        )
        Box(
            modifier = Modifier
                .padding(top = if (isContentBehindTopBar) 0.dp else appBarHeight)
                .focusRequester(contentFocusRequester)
                .onFocusChanged { contentHasFocus = it.hasFocus }
                .focusGroup()
        ) {
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
                                gridState = gridState,
                                contentPadding = contentPadding
                            )
                        }
                    }
                else ->
                    GalleryAlbumGrid(
                        albums = state.albums,
                        onAlbumClick = { albumId = it.id },
                        gridState = gridState,
                        albumIdToFocus = albumIdToFocus,
                        onAlbumFocused = { albumIdToFocus = null },
                        contentPadding = contentPadding
                    )
            }
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
                R.drawable.share_icon_control_normal_24dp, stringResource(R.string.share),
                onClick = onShare
            )
            ActionButton(
                R.drawable.delete_icon_control_normal_24dp, stringResource(R.string.delete),
                onClick = onDelete
            )
            ActionButton(
                R.drawable.select_all_icon_control_normal_24dp, stringResource(R.string.select_all),
                onClick = onSelectAll
            )
        }
    )
}

private val GalleryTab.iconRes: Int
    get() =
        when (this) {
            GalleryTab.PHOTOS -> R.drawable.image_icon_white_24dp
            GalleryTab.ALBUMS -> R.drawable.gallery_icon_white_24dp
        }

private val GalleryTab.labelRes: Int
    get() =
        when (this) {
            GalleryTab.PHOTOS -> R.string.gallery_photos
            GalleryTab.ALBUMS -> R.string.gallery_albums
        }

// A switch in the app bar, which leaves the whole height to the media unlike a bottom bar.
@Composable
private fun GalleryTabSwitch(tab: GalleryTab, onTabChange: (GalleryTab) -> Unit) {
    val appColors = LocalAppColors.current
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = appColors.onAppBar,
        activeContentColor = appColors.appBarContainer,
        activeBorderColor = appColors.onAppBar,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = appColors.onAppBar,
        inactiveBorderColor = appColors.onAppBar
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 12.dp)) {
        GalleryTab.entries.forEachIndexed { index, item ->
            SegmentedButton(
                selected = item == tab,
                onClick = { onTabChange(item) },
                shape = SegmentedButtonDefaults.itemShape(index, GalleryTab.entries.size),
                colors = colors,
                icon = {},
                label = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.labelRes),
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

private val TELEVISION_MARGIN = 32.dp

private val ALBUM_MIN_WIDTH = 150.dp
private val ALBUM_SHAPE = RoundedCornerShape(16.dp)

@Composable
private fun GalleryAlbumGrid(
    albums: List<GalleryAlbum>,
    onAlbumClick: (GalleryAlbum) -> Unit,
    gridState: LazyGridState,
    albumIdToFocus: Long?,
    onAlbumFocused: () -> Unit,
    contentPadding: PaddingValues
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(ALBUM_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            GalleryAlbumItem(
                album = album,
                isToFocus = album.id == albumIdToFocus,
                onFocused = onAlbumFocused,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

@Composable
private fun GalleryAlbumItem(
    album: GalleryAlbum,
    isToFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    if (isToFocus) {
        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
            onFocused()
        }
    }
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFocusedScale(isFocused)
    Column(
        modifier = Modifier
            .focusedScale(isFocused) { scale }
            .clip(ALBUM_SHAPE)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
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
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .focusedBorder(isFocused, MaterialTheme.colorScheme.primary, ALBUM_SHAPE),
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
        Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
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

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.ActionButton
import me.zhanghai.android.files.ui.AlbumCard
import me.zhanghai.android.files.ui.AppBarTabSwitch
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.AppTopAppBar
import me.zhanghai.android.files.ui.HideOnScrollTopBar
import me.zhanghai.android.files.ui.MediaLibraryAccessDenied
import me.zhanghai.android.files.ui.MediaLibraryMessage
import me.zhanghai.android.files.ui.NavigateUpButton
import me.zhanghai.android.files.ui.rememberAppBarScrollState
import me.zhanghai.android.files.ui.sampleStatusBarContent
import me.zhanghai.android.files.util.isTelevision

/** The photos and videos of the device by date, and their albums, like the gallery of a phone. */
@Parcelize
data object GalleryDestination : Destination {
    @Composable
    override fun Content() {
        val state by Gallery.flow.collectAsStateWithLifecycle()
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
    val navigator = LocalNavigator.current
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
    val isEdgeToEdge by Settings.EDGE_TO_EDGE.flow.collectAsState()
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
                                AppBarTabSwitch(
                                    tabs = GalleryTab.entries,
                                    selectedTab = tab,
                                    onTabChange = onTabChange,
                                    iconRes = { it.iconRes },
                                    label = { stringResource(it.labelRes) }
                                )
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
                .sampleStatusBarContent(appBarState)
                .focusRequester(contentFocusRequester)
                .onFocusChanged { contentHasFocus = it.hasFocus }
                // Focuses the media focused before again, like when coming back from the viewer.
                .focusRestorer()
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
                                        navigator.navigate(
                                            GalleryViewerDestination(album?.id, it.id)
                                        )
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

private val TELEVISION_MARGIN = 32.dp

private val ALBUM_MIN_WIDTH = 150.dp

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
    AlbumCard(
        cover = album.cover,
        placeholderIconRes = R.drawable.gallery_icon_white_24dp,
        name = album.name,
        subtitle = pluralStringResource(
            R.plurals.gallery_album_item_count_format, album.media.size, album.media.size
        ),
        isToFocus = isToFocus,
        onFocused = onFocused,
        onClick = onClick
    )
}

@Composable
private fun GalleryEmpty() {
    MediaLibraryMessage(R.drawable.gallery_icon_white_24dp, stringResource(R.string.gallery_empty))
}

@Composable
private fun GalleryAccessDenied() {
    MediaLibraryAccessDenied(
        R.drawable.gallery_icon_white_24dp, stringResource(R.string.gallery_access_denied),
        onReload = Gallery::reload
    )
}

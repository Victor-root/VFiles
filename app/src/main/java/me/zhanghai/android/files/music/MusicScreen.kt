/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.ui.AlbumCard
import me.zhanghai.android.files.ui.AppBarTabSwitch
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.AppTopAppBar
import me.zhanghai.android.files.ui.Cover
import me.zhanghai.android.files.ui.MediaLibraryAccessDenied
import me.zhanghai.android.files.ui.MediaLibraryMessage
import me.zhanghai.android.files.ui.NavigateUpButton
import me.zhanghai.android.files.ui.formatDuration

/** The songs of the device, by title, album and artist, like the music player of a phone. */
@Parcelize
data object MusicDestination : Destination {
    @Composable
    override fun Content() {
        val state by Music.flow.collectAsStateWithLifecycle()
        MusicScreen(state)
    }
}

private enum class MusicTab(@param:DrawableRes val iconRes: Int, val labelRes: Int) {
    TRACKS(R.drawable.audio_icon_white_24dp, R.string.music_tracks),
    ALBUMS(R.drawable.album_icon_white_24dp, R.string.music_albums),
    ARTISTS(R.drawable.artist_icon_white_24dp, R.string.music_artists)
}

@Composable
internal fun MusicScreen(state: MusicState?) {
    val navigator = LocalNavigator.current
    val player = rememberMusicController()
    var tab by rememberSaveable { mutableStateOf(MusicTab.TRACKS) }
    var albumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var artistId by rememberSaveable { mutableStateOf<Long?>(null) }
    val album = albumId?.let { state?.getAlbum(it) }
    val artist = artistId?.let { state?.getArtist(it) }
    // An album or an artist whose songs are all gone is closed.
    LaunchedEffect(state, albumId, artistId) {
        val isDetailGone = albumId != null && album == null || artistId != null && artist == null
        if (state != null && isDetailGone) {
            albumId = null
            artistId = null
        }
    }
    // Kept while another tab or an album is shown, while each album and artist has its own.
    val tracksListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val artistsListState = rememberLazyListState()
    // What to focus again once an album or an artist is closed, on a D-pad.
    var idToFocus by remember { mutableStateOf<Long?>(null) }
    val closeDetail = {
        idToFocus = albumId ?: artistId
        albumId = null
        artistId = null
    }
    // On a D-pad, the songs are focused rather than the tabs once they are shown, and again when
    // the focused one is gone, like when opening or closing an album.
    val contentFocusRequester = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(state == null, state?.isAccessDenied, albumId, artistId) {
        if (state != null && inputModeManager.inputMode == InputMode.Keyboard) {
            // Lets the lists lay out their first items, and an album focus itself.
            withFrameNanos {}
            if (!contentHasFocus) {
                contentFocusRequester.requestFocus()
            }
        }
    }
    BackHandler(enabled = album != null || artist != null, onBack = closeDetail)
    val play: (List<MusicTrack>, Int, Boolean) -> Unit = { tracks, index, shuffle ->
        player?.playTracks(tracks, index, shuffle)
    }

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = album?.name ?: artist?.name ?: stringResource(R.string.music_title),
                navigationIcon = {
                    if (album != null || artist != null) {
                        IconButton(onClick = closeDetail) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back_icon_white_24dp),
                                contentDescription = stringResource(R.string.navigate_up)
                            )
                        }
                    } else {
                        NavigateUpButton()
                    }
                },
                actions = {
                    if (album == null && artist == null) {
                        AppBarTabSwitch(
                            tabs = MusicTab.entries,
                            selectedTab = tab,
                            onTabChange = { tab = it },
                            iconRes = { it.iconRes },
                            label = { stringResource(it.labelRes) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (player != null) {
                MiniMusicPlayer(
                    player = player,
                    onClick = { navigator.navigate(MusicPlayerDestination) },
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                    )
                )
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding())
                .focusRequester(contentFocusRequester)
                .onFocusChanged { contentHasFocus = it.hasFocus }
                // Focuses the song focused before again, like when coming back from the player.
                .focusRestorer()
        ) {
            val listPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
            when {
                state == null ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.isAccessDenied ->
                    MediaLibraryAccessDenied(
                        R.drawable.music_library_icon_white_24dp,
                        stringResource(R.string.music_access_denied), onReload = Music::reload
                    )
                state.tracks.isEmpty() ->
                    MediaLibraryMessage(
                        R.drawable.music_library_icon_white_24dp,
                        stringResource(R.string.music_empty)
                    )
                album != null || artist != null -> {
                    val tracks = album?.tracks ?: artist!!.tracks
                    key(albumId, artistId) {
                        TrackList(tracks, rememberLazyListState(), listPadding, play)
                    }
                }
                tab == MusicTab.TRACKS ->
                    TrackList(state.tracks, tracksListState, listPadding, play)
                tab == MusicTab.ALBUMS ->
                    AlbumGrid(
                        albums = state.albums,
                        onAlbumClick = { albumId = it.id },
                        gridState = albumsGridState,
                        albumIdToFocus = idToFocus,
                        onAlbumFocused = { idToFocus = null },
                        contentPadding = listPadding
                    )
                else ->
                    ArtistList(
                        artists = state.artists,
                        onArtistClick = { artistId = it.id },
                        listState = artistsListState,
                        artistIdToFocus = idToFocus,
                        onArtistFocused = { idToFocus = null },
                        contentPadding = listPadding
                    )
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<MusicTrack>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onPlay: (tracks: List<MusicTrack>, index: Int, shuffle: Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding
    ) {
        item(key = "shuffle") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.music_shuffle_all)) },
                modifier = Modifier.clickable {
                    onPlay(tracks, tracks.indices.random(), true)
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.shuffle_icon_white_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            ListItem(
                headlineContent = {
                    Text(text = track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                modifier = Modifier.clickable { onPlay(tracks, index, false) },
                supportingContent = {
                    Text(
                        text = listOf(
                            track.artist ?: stringResource(R.string.music_unknown_artist),
                            formatDuration(track.durationMillis)
                        ).joinToString(" • "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Cover(
                        cover = track,
                        placeholderIconRes = R.drawable.audio_icon_white_24dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            )
        }
    }
}

private val ALBUM_MIN_WIDTH = 150.dp

@Composable
private fun AlbumGrid(
    albums: List<MusicAlbum>,
    onAlbumClick: (MusicAlbum) -> Unit,
    gridState: LazyGridState,
    albumIdToFocus: Long?,
    onAlbumFocused: () -> Unit,
    contentPadding: PaddingValues
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(ALBUM_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
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
            AlbumCard(
                cover = album.cover,
                placeholderIconRes = R.drawable.album_icon_white_24dp,
                name = album.name,
                subtitle = album.artist ?: pluralStringResource(
                    R.plurals.music_track_count_format, album.tracks.size, album.tracks.size
                ),
                isToFocus = album.id == albumIdToFocus,
                onFocused = onAlbumFocused,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

@Composable
private fun ArtistList(
    artists: List<MusicArtist>,
    onArtistClick: (MusicArtist) -> Unit,
    listState: LazyListState,
    artistIdToFocus: Long?,
    onArtistFocused: () -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding
    ) {
        items(artists, key = { it.id }) { artist ->
            val focusRequester = remember { FocusRequester() }
            if (artist.id == artistIdToFocus) {
                LaunchedEffect(focusRequester) {
                    focusRequester.requestFocus()
                    onArtistFocused()
                }
            }
            val trackCount = artist.tracks.size
            ListItem(
                headlineContent = {
                    Text(text = artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .clickable { onArtistClick(artist) },
                supportingContent = {
                    Text(
                        text = listOf(
                            pluralStringResource(
                                R.plurals.music_album_count_format, artist.albums.size,
                                artist.albums.size
                            ),
                            pluralStringResource(
                                R.plurals.music_track_count_format, trackCount, trackCount
                            )
                        ).joinToString(" • "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Cover(
                        cover = artist.albums.first().cover,
                        placeholderIconRes = R.drawable.artist_icon_white_24dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                }
            )
        }
    }
}

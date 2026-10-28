/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.theme.LocalAppColors
import me.zhanghai.android.files.ui.MediaViewerError
import me.zhanghai.android.files.ui.MediaViewerSystemUiEffect
import me.zhanghai.android.files.ui.MediaViewerTopAppBar
import me.zhanghai.android.files.ui.MenuItem
import me.zhanghai.android.files.ui.OverflowMenu
import me.zhanghai.android.files.ui.depthPageTransition
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The photos and videos of the gallery, or of one of its albums, in full screen. */
class GalleryViewerActivity : AppActivity() {
    private val args by args<Args>()

    override val hasDarkSystemBars: Boolean
        get() = true

    @Composable
    override fun Content() {
        val state = GalleryLiveData.observeAsState().value ?: return
        val albumId = args.albumId
        val media = if (albumId != null) state.getAlbum(albumId)?.media.orEmpty() else state.media
        if (media.isEmpty()) {
            LaunchedEffect(Unit) { finish() }
            return
        }
        GalleryViewerScreen(media, args.mediaId)
    }

    /** The photos and videos are those of the album when there's one. */
    @Parcelize
    class Args(val albumId: Long?, val mediaId: Long) : ParcelableArgs
}

// The height of the bar of actions at the bottom, and of the carousel above them, above which the
// controls of videos are.
private val BOTTOM_BAR_HEIGHT = 48.dp
private val CAROUSEL_ITEM_SIZE = 40.dp
private val CAROUSEL_PADDING_TOP = 8.dp
private val CAROUSEL_HEIGHT = CAROUSEL_ITEM_SIZE + CAROUSEL_PADDING_TOP
private val BAR_COLOR = Color.Black.copy(alpha = 0.5f)
// Stands out on the bars, where the usual overlay is hard to see.
private val FOCUSED_COLOR = Color.White.copy(alpha = 0.3f)

@Composable
private fun GalleryViewerScreen(media: List<GalleryMedia>, initialMediaId: Long) {
    val context = LocalContext.current
    var currentMediaId by rememberSaveable { mutableLongStateOf(initialMediaId) }
    val pagerState = rememberPagerState(
        initialPage = media.indexOfFirst { it.id == currentMediaId }.coerceAtLeast(0)
    ) { media.size }
    // Stays on the same media when the list changes, or on the next one when it's gone.
    LaunchedEffect(media) {
        val index = media.indexOfFirst { it.id == currentMediaId }
        if (index == -1) {
            currentMediaId = media[pagerState.currentPage.coerceIn(media.indices)].id
        } else if (index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
    }
    LaunchedEffect(pagerState, media) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            media.getOrNull(page)?.let { currentMediaId = it.id }
        }
    }
    val currentMedia = media[pagerState.currentPage.coerceIn(media.indices)]
    var isUiShown by rememberSaveable { mutableStateOf(true) }
    var isDetailsShown by rememberSaveable { mutableStateOf(false) }
    val deleteMedia = rememberGalleryMediaDeleter(onDeleted = {})
    val coroutineScope = rememberCoroutineScope()
    // Only the settled video has a player, which plays it from the start.
    val settledMedia = media[pagerState.settledPage.coerceIn(media.indices)]
    val player = if (settledMedia.isVideo) rememberGalleryVideoPlayer(settledMedia) else null
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var isControlFocused by remember { mutableStateOf(false) }
    val bottomBarFocusRequester = remember { FocusRequester() }
    MediaViewerSystemUiEffect(isUiShown)
    // On a remote, back from the controls goes back to the media.
    BackHandler(enabled = isControlFocused) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                isControlFocused = it.hasFocus && !it.isFocused
            }
            .onKeyEvent {
                if (!isFocused || it.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                when (it.key) {
                    // A remote's left and right keys go to the previous and next media.
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val page = pagerState.currentPage +
                            if (it.key == Key.DirectionLeft) -1 else 1
                        if (page in media.indices) {
                            coroutineScope.launch { pagerState.animateScrollToPage(page) }
                        }
                    }
                    // Its center key plays or pauses a video, and shows or hides the bars of a
                    // photo, like a tap.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause ->
                        if (player != null) {
                            player.playOrPause()
                        } else {
                            isUiShown = !isUiShown
                        }
                    // Its down key goes to the actions.
                    Key.DirectionDown -> {
                        isUiShown = true
                        coroutineScope.launch {
                            // Lets the bars appear.
                            withFrameNanos {}
                            bottomBarFocusRequester.requestFocus()
                        }
                    }
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusable()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { media[it].id }
        ) { page ->
            val pageMedia = media[page]
            // Pages on the right are drawn behind the current one.
            val pageModifier = Modifier
                .zIndex(-page.toFloat())
                .depthPageTransition(pagerState, page)
            val toggleUi = { isUiShown = !isUiShown }
            if (pageMedia.isVideo) {
                GalleryVideoPage(
                    media = pageMedia,
                    player = player.takeIf { pageMedia == settledMedia },
                    isControlsShown = isUiShown,
                    onClick = toggleUi,
                    modifier = pageModifier
                )
            } else {
                GalleryPhotoPage(media = pageMedia, onClick = toggleUi, modifier = pageModifier)
            }
        }
        AnimatedVisibility(
            visible = isUiShown,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            val dateTime = Instant.ofEpochMilli(currentMedia.dateTaken)
                .atZone(ZoneId.systemDefault())
            val locale = LocalConfiguration.current.locales[0]
            MediaViewerTopAppBar(
                title = DateTimeFormatter.ofPattern(
                    DateFormat.getBestDateTimePattern(locale, "dMMMMyyyy"), locale
                ).format(dateTime),
                subtitle = DateFormat.getTimeFormat(context).format(currentMedia.dateTaken),
                actions = {
                    OverflowMenu { dismiss ->
                        MenuItem(
                            R.drawable.open_with_icon_control_normal_24dp,
                            stringResource(R.string.file_item_action_open_with)
                        ) {
                            dismiss()
                            context.openGalleryMediaWith(currentMedia)
                        }
                        MenuItem(
                            R.drawable.folder_icon_control_normal_24dp,
                            stringResource(R.string.gallery_show_in_folder)
                        ) {
                            dismiss()
                            context.showGalleryMediaInFolder(currentMedia)
                        }
                    }
                }
            )
        }
        AnimatedVisibility(
            visible = isUiShown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            GalleryViewerBottomBar(
                media = media,
                currentIndex = pagerState.currentPage,
                onCarouselClick = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                focusRequester = bottomBarFocusRequester,
                onShare = { context.shareGalleryMedia(listOf(currentMedia)) },
                onDetails = { isDetailsShown = true },
                onDelete = { deleteMedia(listOf(currentMedia)) }
            )
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    if (isDetailsShown) {
        GalleryDetailsSheet(currentMedia, onDismissRequest = { isDetailsShown = false })
    }
}

@Composable
private fun GalleryViewerBottomBar(
    media: List<GalleryMedia>,
    currentIndex: Int,
    onCarouselClick: (Int) -> Unit,
    focusRequester: FocusRequester,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAppColors.current.appBarContainer)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        GalleryViewerCarousel(media, currentIndex, onCarouselClick)
        GalleryViewerActions(focusRequester, onShare, onDetails, onDelete)
    }
}

// The media around the current one, small under the media which already has the bars over it.
@Composable
private fun GalleryViewerCarousel(
    media: List<GalleryMedia>,
    currentIndex: Int,
    onClick: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(CAROUSEL_HEIGHT)
            .padding(top = CAROUSEL_PADDING_TOP)
    ) {
        // Any media can be in the middle, where the current one is.
        val sidePadding = (maxWidth - CAROUSEL_ITEM_SIZE) / 2
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
        LaunchedEffect(listState, currentIndex) {
            if (listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }) {
                listState.animateScrollToItem(currentIndex)
            } else {
                listState.scrollToItem(currentIndex)
            }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(CAROUSEL_ITEM_SPACING)
        ) {
            itemsIndexed(media, key = { _, item -> item.id }) { index, item ->
                GalleryViewerCarouselItem(
                    media = item,
                    isCurrent = index == currentIndex,
                    onClick = { onClick(index) }
                )
            }
        }
    }
}

private val CAROUSEL_ITEM_SPACING = 4.dp
private val CAROUSEL_ITEM_SHAPE = RoundedCornerShape(6.dp)

@Composable
private fun GalleryViewerCarouselItem(media: GalleryMedia, isCurrent: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val onAppBar = LocalAppColors.current.onAppBar
    AsyncImage(
        model = media,
        contentDescription = null,
        modifier = Modifier
            .size(CAROUSEL_ITEM_SIZE)
            .graphicsLayer { alpha = if (isCurrent || isFocused) 1f else CAROUSEL_OTHER_ALPHA }
            .clip(CAROUSEL_ITEM_SHAPE)
            // Shown while the thumbnail loads.
            .background(onAppBar.copy(alpha = CAROUSEL_PLACEHOLDER_ALPHA))
            .focusedBorder(isCurrent || isFocused, onAppBar, CAROUSEL_ITEM_SHAPE)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop
    )
}

private const val CAROUSEL_OTHER_ALPHA = 0.6f
private const val CAROUSEL_PLACEHOLDER_ALPHA = 0.2f

@Composable
private fun GalleryViewerActions(
    focusRequester: FocusRequester,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusGroup()
            .fillMaxWidth()
            .height(BOTTOM_BAR_HEIGHT),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GalleryViewerAction(
            R.drawable.share_icon_control_normal_24dp, stringResource(R.string.share), onShare
        )
        GalleryViewerAction(
            R.drawable.information_icon_white_24dp, stringResource(R.string.gallery_details),
            onDetails
        )
        GalleryViewerAction(
            R.drawable.delete_icon_control_normal_24dp, stringResource(R.string.delete), onDelete
        )
    }
}

@Composable
private fun GalleryViewerAction(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) FOCUSED_COLOR else Color.Transparent, CircleShape)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = LocalAppColors.current.onAppBar
        )
    }
}

@Composable
private fun GalleryPhotoPage(media: GalleryMedia, onClick: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var error by remember(media) { mutableStateOf<Throwable?>(null) }
    val zoomableImageState = rememberZoomableImageState()
    val request = remember(context, media) {
        ImageRequest.Builder(context)
            .data(media.uri)
            .listener(
                onError = { _, result ->
                    result.throwable.printStackTrace()
                    error = result.throwable
                }
            )
            .build()
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val currentError = error
        if (currentError != null) {
            MediaViewerError(currentError)
            return@Box
        }
        // The thumbnail is shown at once, while the photo itself loads.
        if (!zoomableImageState.isImageDisplayed) {
            AsyncImage(
                model = media,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        ZoomableAsyncImage(
            model = request,
            contentDescription = media.displayName,
            modifier = Modifier.fillMaxSize(),
            state = zoomableImageState,
            onClick = { onClick() }
        )
    }
}

@Composable
private fun rememberGalleryVideoPlayer(media: GalleryMedia): ExoPlayer {
    val context = LocalContext.current
    val player = remember(context, media) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    // Paused when leaving the app.
    LifecycleStartEffect(player) { onStopOrDispose { player.pause() } }
    return player
}

// Like the play and pause button, from the start again once ended.
@androidx.annotation.OptIn(UnstableApi::class)
private fun ExoPlayer.playOrPause() {
    Util.handlePlayPauseButtonAction(this)
}

@Composable
private fun GalleryVideoPage(
    media: GalleryMedia,
    player: ExoPlayer?,
    isControlsShown: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (player != null) {
            GalleryVideoPlayer(media, player, isControlsShown)
        } else {
            GalleryVideoThumbnail(media)
        }
    }
}

@Composable
private fun GalleryVideoThumbnail(media: GalleryMedia) {
    AsyncImage(
        model = media,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}

// Lint checks the unstable APIs of Media3 against the opt-in of AndroidX.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun GalleryVideoPlayer(media: GalleryMedia, player: ExoPlayer, isControlsShown: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        // A texture view, unlike a surface view, follows the transition between pages.
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            shutter = { GalleryVideoThumbnail(media) }
        )
        AnimatedVisibility(
            visible = isControlsShown,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GalleryVideoControls(player)
        }
    }
}

private const val PROGRESS_TICK_INTERVAL_MILLIS = 200L

// Lint checks the unstable APIs of Media3 against the opt-in of AndroidX.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun GalleryVideoControls(player: ExoPlayer) {
    Box(modifier = Modifier.fillMaxSize()) {
        val playPauseState = rememberPlayPauseButtonState(player)
        var isPlayPauseFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = playPauseState::onClick,
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .onFocusChanged { isPlayPauseFocused = it.isFocused }
                .background(if (isPlayPauseFocused) FOCUSED_COLOR else BAR_COLOR, CircleShape),
            enabled = playPauseState.isEnabled
        ) {
            Icon(
                painter = painterResource(
                    if (playPauseState.showPlay) {
                        R.drawable.play_icon_white_24dp
                    } else {
                        R.drawable.pause_icon_white_24dp
                    }
                ),
                contentDescription = stringResource(
                    if (playPauseState.showPlay) R.string.gallery_play else R.string.gallery_pause
                ),
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }
        val progressState = rememberProgressStateWithTickInterval(
            player, PROGRESS_TICK_INTERVAL_MILLIS
        )
        val durationMillis = progressState.durationMs.coerceAtLeast(0)
        // While dragging, the slider shows where the video will go instead of where it is.
        var seekFraction by remember { mutableStateOf<Float?>(null) }
        val positionFraction = if (durationMillis > 0) {
            progressState.currentPositionMs.toFloat() / durationMillis
        } else {
            0f
        }
        val fraction = seekFraction ?: positionFraction
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    start = 16.dp, end = 16.dp, bottom = BOTTOM_BAR_HEIGHT + CAROUSEL_HEIGHT
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryVideoTime((fraction * durationMillis).toLong())
            var isSliderFocused by remember { mutableStateOf(false) }
            Slider(
                value = fraction.coerceIn(0f, 1f),
                onValueChange = { seekFraction = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .onFocusChanged { isSliderFocused = it.hasFocus }
                    .background(
                        if (isSliderFocused) FOCUSED_COLOR else Color.Transparent, CircleShape
                    )
                    .padding(horizontal = 8.dp),
                onValueChangeFinished = {
                    seekFraction?.let { player.seekTo((it * durationMillis).toLong()) }
                    seekFraction = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            GalleryVideoTime(durationMillis)
        }
    }
}

@Composable
private fun GalleryVideoTime(millis: Long) {
    Text(
        text = formatDuration(millis),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryDetailsSheet(media: GalleryMedia, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.gallery_details),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.titleLarge
            )
            GalleryDetail(stringResource(R.string.file_properties_basic_name), media.displayName)
            GalleryDetail(
                stringResource(R.string.gallery_details_date),
                DateUtils.formatDateTime(
                    context, media.dateTaken,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or
                        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_TIME
                )
            )
            if (media.width > 0 && media.height > 0) {
                GalleryDetail(
                    stringResource(R.string.file_properties_media_dimensions),
                    stringResource(
                        R.string.file_properties_media_dimensions_format, media.width, media.height
                    )
                )
            }
            if (media.isVideo) {
                GalleryDetail(
                    stringResource(R.string.file_properties_media_duration),
                    formatDuration(media.durationMillis)
                )
            }
            GalleryDetail(
                stringResource(R.string.file_properties_basic_size),
                media.size.asFileSize().formatHumanReadable(context)
            )
            GalleryDetail(
                stringResource(R.string.file_properties_basic_parent_directory),
                media.data.substringBeforeLast('/')
            )
        }
    }
}

@Composable
private fun GalleryDetail(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

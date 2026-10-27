/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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

// The height of the bar of actions at the bottom, above which the controls of videos are.
private val BOTTOM_BAR_HEIGHT = 64.dp
private val BAR_COLOR = Color.Black.copy(alpha = 0.5f)

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
    val focusRequester = remember { FocusRequester() }
    MediaViewerSystemUiEffect(isUiShown)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // A remote's left and right keys go to the previous and next media.
            .focusRequester(focusRequester)
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                val page = when (it.key) {
                    Key.DirectionLeft -> pagerState.currentPage - 1
                    Key.DirectionRight -> pagerState.currentPage + 1
                    else -> return@onKeyEvent false
                }
                if (page in media.indices) {
                    coroutineScope.launch { pagerState.animateScrollToPage(page) }
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
                    isCurrent = page == pagerState.settledPage,
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
private fun GalleryViewerBottomBar(onShare: () -> Unit, onDetails: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BAR_COLOR)
            .windowInsetsPadding(WindowInsets.navigationBars)
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
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White
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
private fun GalleryVideoPage(
    media: GalleryMedia,
    isCurrent: Boolean,
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
        // Only the current video has a player, which plays it from the start.
        if (isCurrent) {
            GalleryVideoPlayer(media, isControlsShown)
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
private fun GalleryVideoPlayer(media: GalleryMedia, isControlsShown: Boolean) {
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
        IconButton(
            onClick = playPauseState::onClick,
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .background(BAR_COLOR, CircleShape),
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
                .padding(start = 16.dp, end = 16.dp, bottom = BOTTOM_BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryVideoTime((fraction * durationMillis).toLong())
            Slider(
                value = fraction.coerceIn(0f, 1f),
                onValueChange = { seekFraction = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
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

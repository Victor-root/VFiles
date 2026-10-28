/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.FastScrollable
import me.zhanghai.android.files.ui.FastScroller
import me.zhanghai.android.files.util.isTelevision
import me.zhanghai.android.files.viewer.video.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val THUMBNAIL_MIN_WIDTH = 96.dp
// Seen from afar on a TV.
private val TELEVISION_THUMBNAIL_MIN_WIDTH = 144.dp
private val THUMBNAIL_SPACING = 2.dp
private const val MIN_COLUMN_COUNT = 2
private val MAX_COLUMN_MIN_WIDTH = 48.dp
private const val PINCH_ZOOM_THRESHOLD = 1.25f

/**
 * The photos and videos in a grid, under the day they were taken, or the month when zoomed out.
 * Pinching changes the number of columns. While [selectedIds] isn't empty, clicks toggle the
 * selection, and so do the headers for all of their media.
 */
@Composable
fun GalleryMediaGrid(
    media: List<GalleryMedia>,
    selectedIds: Set<Long>,
    onMediaClick: (GalleryMedia) -> Unit,
    onMediaLongClick: (GalleryMedia) -> Unit,
    onSectionClick: (List<GalleryMedia>) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val isTelevision = remember(context) { context.isTelevision }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val thumbnailMinWidth =
            if (isTelevision) TELEVISION_THUMBNAIL_MIN_WIDTH else THUMBNAIL_MIN_WIDTH
        val defaultColumnCount = (maxWidth / thumbnailMinWidth).toInt().coerceAtLeast(3)
        val maxColumnCount = (maxWidth / MAX_COLUMN_MIN_WIDTH).toInt()
            .coerceAtLeast(defaultColumnCount)
        val currentMaxColumnCount by rememberUpdatedState(maxColumnCount)
        var columnCount by rememberSaveable { mutableIntStateOf(defaultColumnCount) }
        // Zoomed out, the days are too small to be told apart.
        val isByMonth = columnCount > defaultColumnCount + 1
        val entries = remember(media, isByMonth, context) {
            buildGridEntries(media, isByMonth, context)
        }
        val isSelecting = selectedIds.isNotEmpty()
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom(
                    onZoomIn = {
                        columnCount = (columnCount - 1).coerceAtLeast(MIN_COLUMN_COUNT)
                    },
                    onZoomOut = {
                        columnCount = (columnCount + 1).coerceAtMost(currentMaxColumnCount)
                    }
                ),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(THUMBNAIL_SPACING),
            verticalArrangement = Arrangement.spacedBy(THUMBNAIL_SPACING)
        ) {
            items(
                entries,
                key = { it.key },
                span = { if (it is GridHeader) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { it::class }
            ) { entry ->
                when (entry) {
                    is GridHeader -> GalleryGridHeader(
                        title = entry.title,
                        isSelecting = isSelecting,
                        isSelected = entry.media.all { it.id in selectedIds },
                        onClick = { onSectionClick(entry.media) },
                        modifier = Modifier.animateItem()
                    )
                    is GridMedia -> GalleryThumbnail(
                        media = entry.media,
                        isSelecting = isSelecting,
                        isSelected = entry.media.id in selectedIds,
                        onClick = { onMediaClick(entry.media) },
                        onLongClick = { onMediaLongClick(entry.media) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        val fastScrollable = remember(gridState) { GalleryFastScrollable(gridState) }
        FastScroller(
            scrollable = fastScrollable,
            modifier = Modifier.align(Alignment.TopEnd),
            contentPadding = contentPadding,
            popupText = {
                entries.getOrNull(gridState.firstVisibleItemIndex)?.monthTitle.orEmpty()
            }
        )
    }
}

private sealed interface GridEntry {
    val key: Any
    val monthTitle: String
}

private class GridHeader(
    val title: String,
    override val monthTitle: String,
    val media: List<GalleryMedia>
) : GridEntry {
    override val key: Any
        get() = "header:$title"
}

private class GridMedia(val media: GalleryMedia, override val monthTitle: String) : GridEntry {
    override val key: Any
        get() = media.id
}

private fun buildGridEntries(
    media: List<GalleryMedia>,
    isByMonth: Boolean,
    context: Context
): List<GridEntry> {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val locale = Locale.getDefault()
    val dayFormatter = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale
    )
    val dayWithYearFormatter = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy"), locale
    )
    val monthFormatter = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale
    )
    val entries = mutableListOf<GridEntry>()
    val sections = media.groupBy {
        val date = Instant.ofEpochMilli(it.dateTaken).atZone(zoneId).toLocalDate()
        if (isByMonth) date.withDayOfMonth(1) else date
    }
    for ((date, sectionMedia) in sections) {
        val monthTitle = monthFormatter.format(date).capitalize(locale)
        val title = when {
            isByMonth -> monthTitle
            date == today -> context.getString(R.string.gallery_today)
            date == today.minusDays(1) -> context.getString(R.string.gallery_yesterday)
            date.year == today.year -> dayFormatter.format(date).capitalize(locale)
            else -> dayWithYearFormatter.format(date).capitalize(locale)
        }
        entries += GridHeader(title, monthTitle, sectionMedia)
        sectionMedia.mapTo(entries) { GridMedia(it, monthTitle) }
    }
    return entries
}

private fun String.capitalize(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }

// The thumb follows the rows rather than the pixels, since the headers aren't as tall as them.
private class GalleryFastScrollable(private val gridState: LazyGridState) : FastScrollable {
    private val maxIndex: Int
        get() {
            val layoutInfo = gridState.layoutInfo
            return (layoutInfo.totalItemsCount - layoutInfo.visibleItemsInfo.size)
                .coerceAtLeast(0)
        }

    override val scrollFraction: Float
        get() {
            val maxIndex = maxIndex
            return if (maxIndex > 0) {
                (gridState.firstVisibleItemIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    override val canScroll: Boolean
        get() = gridState.canScrollForward || gridState.canScrollBackward

    override val isScrollInProgress: Boolean
        get() = gridState.isScrollInProgress

    override suspend fun scrollToFraction(fraction: Float) {
        gridState.scrollToItem((fraction * maxIndex).roundToInt())
    }
}

// Two fingers moving apart or together change the number of columns, without scrolling the grid.
private fun Modifier.pinchToZoom(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        zoom *= event.calculateZoom()
                        event.changes.forEach { it.consume() }
                        if (zoom > PINCH_ZOOM_THRESHOLD) {
                            onZoomIn()
                            zoom = 1f
                        } else if (zoom < 1 / PINCH_ZOOM_THRESHOLD) {
                            onZoomOut()
                            zoom = 1f
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    )

@Composable
private fun GalleryGridHeader(
    title: String,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isSelecting, onClick = onClick)
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall
        )
        if (isSelecting) {
            Spacer(modifier = Modifier.width(16.dp))
            SelectionIndicator(isSelected = isSelected, isOverMedia = false)
        }
    }
}

private const val FOCUSED_SCALE = 1.08f
private val FOCUSED_BORDER_WIDTH = 3.dp

/** The scale of an item growing while focused, which stands out more than an overlay on photos. */
@Composable
internal fun animateFocusedScale(isFocused: Boolean): State<Float> =
    animateFloatAsState(if (isFocused) FOCUSED_SCALE else 1f)

/** Draws a focused item grown by [scale] above its neighbors. */
internal fun Modifier.focusedScale(isFocused: Boolean, scale: () -> Float): Modifier =
    zIndex(if (isFocused) 1f else 0f)
        .graphicsLayer {
            val currentScale = scale()
            scaleX = currentScale
            scaleY = currentScale
        }

/** Outlines a focused item in the accent color. */
internal fun Modifier.focusedBorder(isFocused: Boolean, color: Color, shape: Shape): Modifier =
    if (isFocused) border(FOCUSED_BORDER_WIDTH, color, shape) else this

private val SELECTED_INSET = 12.dp
private val SELECTED_CORNER_RADIUS = 12.dp
private val VIDEO_LABEL_SHADOW = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryThumbnail(
    media: GalleryMedia,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A selected item shrinks with rounded corners, like in the gallery of a phone.
    val inset by animateDpAsState(if (isSelected) SELECTED_INSET else 0.dp)
    val cornerRadius by animateDpAsState(if (isSelected) SELECTED_CORNER_RADIUS else 0.dp)
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFocusedScale(isFocused)
    Box(
        modifier = modifier
            .focusedScale(isFocused) { scale }
            .aspectRatio(1f)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        val shape = RoundedCornerShape(cornerRadius)
        AsyncImage(
            model = media,
            contentDescription = media.displayName,
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .focusedBorder(isFocused, MaterialTheme.colorScheme.primary, shape),
            contentScale = ContentScale.Crop
        )
        if (media.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(inset)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(media.durationMillis),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(shadow = VIDEO_LABEL_SHADOW)
                )
                Icon(
                    painter = painterResource(R.drawable.play_icon_white_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
            }
        }
        if (isSelecting) {
            SelectionIndicator(
                isSelected = isSelected,
                isOverMedia = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun SelectionIndicator(
    isSelected: Boolean,
    isOverMedia: Boolean,
    modifier: Modifier = Modifier
) {
    val size = 22.dp
    if (isSelected) {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.check_icon_white_24dp),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(
                    if (isOverMedia) Color.Black.copy(alpha = 0.2f) else Color.Transparent,
                    CircleShape
                )
                .border(
                    2.dp,
                    if (isOverMedia) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    CircleShape
                )
        )
    }
}

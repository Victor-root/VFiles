/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filejob.FileJobProgress
import me.zhanghai.android.files.filejob.FileJobProgresses
import me.zhanghai.android.files.filejob.FileJobService
import java.text.NumberFormat

/**
 * The title and subtitle of the file list app bar. While file jobs run, the subtitle shows their
 * overall progress instead, and the title opens a popover listing them below it.
 */
@Composable
fun FileListAppBarTitle(
    title: String,
    subtitle: String,
    isTitleScrolling: Boolean,
    contentColor: Color
) {
    val progresses by FileJobProgresses.flow.collectAsStateWithLifecycle()
    val isRunning = progresses.isNotEmpty()
    var isPopoverShown by remember { mutableStateOf(false) }
    // Closed with the last job, so that it doesn't open again on its own with the next one.
    LaunchedEffect(isRunning) {
        if (!isRunning) {
            isPopoverShown = false
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    // A tinted background tells the title can be clicked, stronger when focused with a D-pad.
    val backgroundAlpha by animateFloatAsState(
        when {
            !isRunning -> 0f
            isFocused -> FOCUSED_BACKGROUND_ALPHA
            else -> BACKGROUND_ALPHA
        }
    )
    Box {
        Column(
            modifier = Modifier
                // Keeps the text in place, with the background around it.
                .offset(x = -TITLE_HORIZONTAL_PADDING)
                .clip(TITLE_SHAPE)
                .background(contentColor.copy(alpha = backgroundAlpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = isRunning,
                    onClickLabel = stringResource(R.string.file_job_progress_title)
                ) { isPopoverShown = !isPopoverShown }
                .padding(horizontal = TITLE_HORIZONTAL_PADDING, vertical = 2.dp)
        ) {
            Text(
                text = title,
                // A long title, like when picking files for another app, scrolls to be read.
                modifier = if (isTitleScrolling) {
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                } else {
                    Modifier
                },
                maxLines = 1,
                overflow = if (isTitleScrolling) TextOverflow.Clip else TextOverflow.Ellipsis
            )
            Crossfade(targetState = isRunning) { showsProgress ->
                if (showsProgress) {
                    FileJobProgressSubtitle(progresses, contentColor)
                } else {
                    Crossfade(targetState = subtitle) {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        if (isRunning && isPopoverShown) {
            Popup(
                popupPositionProvider = BelowPositionProvider,
                onDismissRequest = { isPopoverShown = false },
                properties = PopupProperties(focusable = true)
            ) {
                FileJobProgressPopover(progresses)
            }
        }
    }
}

/** The count of the file jobs running, and their overall progress when known. */
@Composable
private fun FileJobProgressSubtitle(progresses: List<FileJobProgress>, contentColor: Color) {
    // The average of the jobs whose progress is known.
    val fractions = progresses.mapNotNull { it.fraction }
    val overallFraction = if (fractions.isNotEmpty()) fractions.average().toFloat() else null
    val animatedFraction by animateFloatAsState(overallFraction ?: 0f)
    val locale = LocalConfiguration.current.locales[0]
    val text = listOfNotNull(
        pluralStringResource(
            R.plurals.file_list_subtitle_file_job_count_format, progresses.size, progresses.size
        ),
        overallFraction?.let { NumberFormat.getPercentInstance(locale).format(it) }
    )
        .joinToString(stringResource(R.string.file_list_subtitle_separator))
    Row(verticalAlignment = Alignment.CenterVertically) {
        val indicatorModifier = Modifier
            .padding(end = 6.dp)
            .size(14.dp)
        val trackColor = contentColor.copy(alpha = TRACK_ALPHA)
        if (overallFraction != null) {
            CircularProgressIndicator(
                progress = { animatedFraction },
                modifier = indicatorModifier,
                color = contentColor,
                strokeWidth = 2.dp,
                trackColor = trackColor
            )
        } else {
            CircularProgressIndicator(
                modifier = indicatorModifier,
                color = contentColor,
                strokeWidth = 2.dp,
                trackColor = trackColor
            )
        }
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// Places the popover below its anchor, aligned to its start but kept within the window. The margins
// around it are part of the popover.
private object BelowPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        return IntOffset(x.coerceIn(0, maxX), anchorBounds.bottom)
    }
}

private val TITLE_HORIZONTAL_PADDING = 8.dp
private val TITLE_SHAPE = RoundedCornerShape(12.dp)
private const val BACKGROUND_ALPHA = 0.14f
private const val FOCUSED_BACKGROUND_ALPHA = 0.3f
private const val TRACK_ALPHA = 0.35f

@Composable
private fun FileJobProgressPopover(progresses: List<FileJobProgress>) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp)
            .width(320.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(R.string.file_job_progress_title),
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium
            )
            for (progress in progresses) {
                FileJobProgressRow(progress)
            }
        }
    }
}

@Composable
private fun FileJobProgressRow(progress: FileJobProgress) {
    val fraction by animateFloatAsState(progress.fraction ?: 0f)
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = progress.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!progress.detail.isNullOrEmpty()) {
                Text(
                    text = progress.detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
        IconButton(
            onClick = { FileJobService.cancelJob(progress.id) },
            modifier = Modifier
                .padding(start = 4.dp)
                .size(40.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.close_icon_white_24dp),
                contentDescription = stringResource(android.R.string.cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

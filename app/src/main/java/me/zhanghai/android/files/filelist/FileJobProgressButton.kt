/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filejob.FileJobProgress
import me.zhanghai.android.files.filejob.FileJobProgressLiveData
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.util.observeAsStateCompat

/**
 * A round button showing the overall progress and the count of the file jobs running, which opens
 * a popover above it listing them. Nothing is shown when no job is running.
 */
@Composable
fun FileJobProgressButton(modifier: Modifier = Modifier) {
    val progresses by FileJobProgressLiveData.observeAsStateCompat()
    if (progresses.isEmpty()) {
        return
    }
    var isPopoverShown by remember { mutableStateOf(false) }
    // The average of the jobs whose progress is known.
    val fractions = progresses.mapNotNull { it.fraction }
    val overallFraction by animateFloatAsState(
        if (fractions.isNotEmpty()) fractions.average().toFloat() else 0f
    )
    val title = stringResource(R.string.file_job_progress_title)
    Box(modifier = modifier) {
        Surface(
            onClick = { isPopoverShown = !isPopoverShown },
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = title },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { overallFraction },
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
                Text(
                    text = progresses.size.toString(),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (isPopoverShown) {
            Popup(
                popupPositionProvider = AbovePositionProvider,
                onDismissRequest = { isPopoverShown = false },
                properties = PopupProperties(focusable = true)
            ) {
                FileJobProgressPopover(progresses)
            }
        }
    }
}

// Places the popover above its anchor, aligned to its start. The margin between them is part of the
// popover.
private object AbovePositionProvider : PopupPositionProvider {
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
        return IntOffset(x, anchorBounds.top - popupContentSize.height)
    }
}

@Composable
private fun FileJobProgressPopover(progresses: List<FileJobProgress>) {
    Surface(
        modifier = Modifier
            .padding(bottom = 8.dp)
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

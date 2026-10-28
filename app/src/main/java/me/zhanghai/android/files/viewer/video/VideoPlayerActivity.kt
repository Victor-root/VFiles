/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.video

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.ui.MediaViewerSystemUiEffect
import me.zhanghai.android.files.ui.MediaViewerTopAppBar
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.isTelevision

/** Plays a video opened from the files or from another app, like the player of the gallery. */
class VideoPlayerActivity : AppActivity() {
    override val hasDarkSystemBars: Boolean
        get() = true

    @Composable
    override fun Content() {
        val uri = intent.data
        if (uri == null) {
            LaunchedEffect(Unit) { finish() }
            return
        }
        VideoPlayerScreen(uri, intent.extraPath?.fileName?.toString())
    }
}

// How far the left and right keys of a remote move in the video.
private const val SEEK_INCREMENT_MILLIS = 10_000L

@Composable
private fun VideoPlayerScreen(uri: Uri, fileName: String?) {
    val context = LocalContext.current
    val title by produceState(fileName ?: uri.lastPathSegment.orEmpty(), context, uri) {
        if (fileName == null) {
            context.queryDisplayName(uri)?.let { value = it }
        }
    }
    val player = rememberVideoPlayer(uri)
    // On a TV, the bars are hidden while playing, and only there to tell where the video is.
    val isTelevision = remember(context) { context.isTelevision }
    var isUiShown by rememberSaveable { mutableStateOf(!isTelevision) }
    val focusRequester = remember { FocusRequester() }
    MediaViewerSystemUiEffect(isUiShown)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                when (it.key) {
                    // A remote's center key plays or pauses the video, with the bars shown while
                    // paused on a TV.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        player.playOrPause()
                        if (isTelevision) {
                            isUiShown = !player.playWhenReady
                        }
                    }
                    // Its left and right keys go back and forth in the video.
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val increment = if (it.key == Key.DirectionLeft) {
                            -SEEK_INCREMENT_MILLIS
                        } else {
                            SEEK_INCREMENT_MILLIS
                        }
                        player.seekTo(player.currentPosition + increment)
                    }
                    // Nothing else is focused on a TV.
                    Key.DirectionUp, Key.DirectionDown -> if (!isTelevision) {
                        return@onKeyEvent false
                    }
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusable()
    ) {
        // A tap shows or hides the bars.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null) { isUiShown = !isUiShown }
        ) {
            VideoPlayer(
                player = player,
                isControlsShown = isUiShown,
                controlsBottomPadding = 16.dp,
                shutter = {}
            )
        }
        AnimatedVisibility(
            visible = isUiShown,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            MediaViewerTopAppBar(title = title, subtitle = null, actions = {})
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

private suspend fun Context.queryDisplayName(uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }
            .onFailure { it.printStackTrace() }
            .getOrNull()
    }

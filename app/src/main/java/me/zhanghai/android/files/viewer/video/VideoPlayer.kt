/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.video

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import me.zhanghai.android.files.R

// The pieces shared by the players of videos, in the gallery and on their own.

private val BAR_COLOR = Color.Black.copy(alpha = 0.5f)
// Stands out over the video, where the usual overlay is hard to see.
private val FOCUSED_COLOR = Color.White.copy(alpha = 0.3f)

/** A player of [uri], which plays it from the start, and pauses when leaving the app. */
@Composable
fun rememberVideoPlayer(uri: Uri): ExoPlayer {
    val context = LocalContext.current
    val player = remember(context, uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
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
fun ExoPlayer.playOrPause() {
    Util.handlePlayPauseButtonAction(this)
}

/**
 * The video of [player] with its controls, which keep [controlsBottomPadding] free below them.
 * [shutter] is shown until the first frame, like a thumbnail.
 */
// Lint checks the unstable APIs of Media3 against the opt-in of AndroidX.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    player: ExoPlayer,
    isControlsShown: Boolean,
    controlsBottomPadding: Dp,
    shutter: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // A texture view, unlike a surface view, follows the transition between pages.
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            shutter = shutter
        )
        AnimatedVisibility(
            visible = isControlsShown,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            VideoControls(player, controlsBottomPadding)
        }
    }
}

private const val PROGRESS_TICK_INTERVAL_MILLIS = 200L

// Lint checks the unstable APIs of Media3 against the opt-in of AndroidX.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoControls(player: ExoPlayer, bottomPadding: Dp) {
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
                    if (playPauseState.showPlay) {
                        R.string.video_player_play
                    } else {
                        R.string.video_player_pause
                    }
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
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoTime((fraction * durationMillis).toLong())
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
            VideoTime(durationMillis)
        }
    }
}

@Composable
private fun VideoTime(millis: Long) {
    Text(
        text = formatDuration(millis),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium
    )
}

/** Like 1:23, or 1:02:03 from an hour, like the galleries of phones. */
fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}

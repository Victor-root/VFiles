/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval

private const val PROGRESS_TICK_INTERVAL_MILLIS = 200L

/**
 * Where [player] is in what it plays, between its time and its duration in [textColor], which can
 * be dragged to go elsewhere. On a D-pad, the bar is shown in [focusedColor] while focused.
 */
// Lint checks the unstable APIs of Media3 against the opt-in of AndroidX.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerProgressBar(
    player: Player,
    sliderColors: SliderColors,
    textColor: Color,
    focusedColor: Color,
    modifier: Modifier = Modifier
) {
    val progressState = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_INTERVAL_MILLIS)
    val durationMillis = progressState.durationMs.coerceAtLeast(0)
    // While dragging, the slider shows where it will go instead of where it is.
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    val positionFraction = if (durationMillis > 0) {
        progressState.currentPositionMs.toFloat() / durationMillis
    } else {
        0f
    }
    val fraction = seekFraction ?: positionFraction
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PlayerTime((fraction * durationMillis).toLong(), textColor)
        var isSliderFocused by remember { mutableStateOf(false) }
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { seekFraction = it },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .onFocusChanged { isSliderFocused = it.hasFocus }
                .background(if (isSliderFocused) focusedColor else Color.Transparent, CircleShape)
                .padding(horizontal = 8.dp),
            onValueChangeFinished = {
                seekFraction?.let { player.seekTo((it * durationMillis).toLong()) }
                seekFraction = null
            },
            colors = sliderColors
        )
        PlayerTime(durationMillis, textColor)
    }
}

@Composable
private fun PlayerTime(millis: Long, color: Color) {
    Text(text = formatDuration(millis), color = color, style = MaterialTheme.typography.labelMedium)
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

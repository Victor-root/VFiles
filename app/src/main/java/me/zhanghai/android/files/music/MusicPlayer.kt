/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.Cover
import me.zhanghai.android.files.ui.PlayerProgressBar

private val COVER_SHAPE = RoundedCornerShape(16.dp)

/** The song playing in big, with all its controls, closed with [onClose]. */
@Composable
fun MusicPlayerScreen(player: Player?, onClose: () -> Unit) {
    AppScaffold(
        title = stringResource(R.string.music_player_title),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.expand_more_icon_white_24dp),
                    contentDescription = stringResource(R.string.close)
                )
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            if (player == null) {
                CircularProgressIndicator()
            } else {
                MusicPlayerContent(player)
            }
        }
    }
}

@Composable
private fun MusicPlayerContent(player: Player) {
    val current = rememberCurrentMediaItem(player)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The cover is next to the controls on wide screens, like TVs, and above them otherwise.
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(
                    cover = current.cover,
                    placeholderIconRes = R.drawable.audio_icon_white_24dp,
                    modifier = Modifier.clip(COVER_SHAPE)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) { MusicPlayerControls(player, current) }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Cover(
                    cover = current.cover,
                    placeholderIconRes = R.drawable.audio_icon_white_24dp,
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .clip(COVER_SHAPE)
                )
                MusicPlayerControls(player, current)
            }
        }
    }
}

// Lint checks the unstable APIs of Media3 against the opt-in of AndroidX.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ColumnScope.MusicPlayerControls(player: Player, current: CurrentMediaItem) {
    Text(
        text = current.title.orEmpty(),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.headlineSmall
    )
    Text(
        text = current.artist ?: stringResource(R.string.music_unknown_artist),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge
    )
    PlayerProgressBar(
        player = player,
        sliderColors = SliderDefaults.colors(),
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    )
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shuffleState = rememberShuffleButtonState(player)
        ToggleControlButton(
            iconRes = R.drawable.shuffle_icon_white_24dp,
            contentDescription = stringResource(R.string.music_shuffle),
            isOn = shuffleState.shuffleOn,
            isEnabled = shuffleState.isEnabled,
            onClick = shuffleState::onClick
        )
        val previousState = rememberPreviousButtonState(player)
        IconButton(onClick = previousState::onClick, enabled = previousState.isEnabled) {
            Icon(
                painter = painterResource(R.drawable.skip_previous_icon_white_24dp),
                contentDescription = stringResource(R.string.music_previous),
                modifier = Modifier.size(32.dp)
            )
        }
        val playPauseState = rememberPlayPauseButtonState(player)
        // Focused first on a D-pad.
        val playPauseFocusRequester = remember { FocusRequester() }
        FilledIconButton(
            onClick = playPauseState::onClick,
            modifier = Modifier
                .size(72.dp)
                .focusRequester(playPauseFocusRequester),
            enabled = playPauseState.isEnabled
        ) {
            PlayPauseIcon(playPauseState.showPlay, Modifier.size(40.dp))
        }
        LaunchedEffect(playPauseFocusRequester) { playPauseFocusRequester.requestFocus() }
        val nextState = rememberNextButtonState(player)
        IconButton(onClick = nextState::onClick, enabled = nextState.isEnabled) {
            Icon(
                painter = painterResource(R.drawable.skip_next_icon_white_24dp),
                contentDescription = stringResource(R.string.music_next),
                modifier = Modifier.size(32.dp)
            )
        }
        val repeatState = rememberRepeatButtonState(player)
        ToggleControlButton(
            iconRes = if (repeatState.repeatModeState == Player.REPEAT_MODE_ONE) {
                R.drawable.repeat_one_icon_white_24dp
            } else {
                R.drawable.repeat_icon_white_24dp
            },
            contentDescription = stringResource(R.string.music_repeat),
            isOn = repeatState.repeatModeState != Player.REPEAT_MODE_OFF,
            isEnabled = repeatState.isEnabled,
            onClick = repeatState::onClick
        )
    }
}

@Composable
private fun ToggleControlButton(
    iconRes: Int,
    contentDescription: String,
    isOn: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = isEnabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (isOn) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) { Icon(painter = painterResource(iconRes), contentDescription = contentDescription) }
}

@Composable
private fun PlayPauseIcon(showPlay: Boolean, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(
            if (showPlay) R.drawable.play_icon_white_24dp else R.drawable.pause_icon_white_24dp
        ),
        contentDescription = stringResource(if (showPlay) R.string.play else R.string.pause),
        modifier = modifier
    )
}

private const val MINI_PLAYER_PROGRESS_TICK_INTERVAL_MILLIS = 1000L

/** The song playing in small, below a library, which [onClick] opens in big. */
// Lint checks the unstable APIs of Media3 against the opt-in of AndroidX.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniMusicPlayer(player: Player, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val current = rememberCurrentMediaItem(player)
    if (current.mediaItem == null) {
        return
    }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            val progressState = rememberProgressStateWithTickInterval(
                player, MINI_PLAYER_PROGRESS_TICK_INTERVAL_MILLIS
            )
            LinearProgressIndicator(
                progress = {
                    val durationMillis = progressState.durationMs
                    if (durationMillis > 0) {
                        progressState.currentPositionMs.toFloat() / durationMillis
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {}
            )
            Row(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(
                    cover = current.cover,
                    placeholderIconRes = R.drawable.audio_icon_white_24dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = current.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = current.artist ?: stringResource(R.string.music_unknown_artist),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                val playPauseState = rememberPlayPauseButtonState(player)
                IconButton(onClick = playPauseState::onClick, enabled = playPauseState.isEnabled) {
                    PlayPauseIcon(playPauseState.showPlay)
                }
                val nextState = rememberNextButtonState(player)
                IconButton(onClick = nextState::onClick, enabled = nextState.isEnabled) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next_icon_white_24dp),
                        contentDescription = stringResource(R.string.music_next)
                    )
                }
            }
        }
    }
}

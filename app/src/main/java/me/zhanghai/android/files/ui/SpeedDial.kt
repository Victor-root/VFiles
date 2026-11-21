/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.theme.LocalAppColors

class SpeedDialAction(
    @param:DrawableRes val iconRes: Int,
    val label: String,
    val onClick: () -> Unit
)

private const val SCRIM_ALPHA = 0.32f
private const val EXPANDED_ROTATION = 135f
private const val ACTION_DURATION_MILLIS = 150
private const val ACTION_STAGGER_MILLIS = 40
private const val ACTION_INITIAL_SCALE = 0.6f
private val ACTION_TRANSFORM_ORIGIN = TransformOrigin(1f, 1f)

/** Dims the content behind an expanded speed dial, and collapses it when touched. */
@Composable
fun SpeedDialScrim(isExpanded: Boolean, onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = isExpanded,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = null, indication = null, onClick = onCollapse)
        )
    }
}

/**
 * A floating action button whose icon turns into a close icon as it expands into labeled
 * [actions] above it.
 */
@Composable
fun SpeedDial(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    @DrawableRes iconRes: Int,
    contentDescription: String,
    actions: List<SpeedDialAction>,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Each action grows out of the button, the closest one first.
        for ((index, action) in actions.withIndex()) {
            val delayMillis = (actions.lastIndex - index) * ACTION_STAGGER_MILLIS
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(ACTION_DURATION_MILLIS, delayMillis)) +
                    scaleIn(
                        tween(ACTION_DURATION_MILLIS, delayMillis),
                        ACTION_INITIAL_SCALE,
                        ACTION_TRANSFORM_ORIGIN
                    ) +
                    slideInVertically(tween(ACTION_DURATION_MILLIS, delayMillis)) { it / 2 },
                exit = fadeOut(tween(ACTION_DURATION_MILLIS)) +
                    scaleOut(
                        tween(ACTION_DURATION_MILLIS), ACTION_INITIAL_SCALE, ACTION_TRANSFORM_ORIGIN
                    )
            ) {
                SpeedDialActionItem(action) {
                    onExpandedChange(false)
                    action.onClick()
                }
            }
        }
        val rotation by animateFloatAsState(if (isExpanded) EXPANDED_ROTATION else 0f)
        FloatingActionButton(
            onClick = { onExpandedChange(!isExpanded) },
            modifier = buttonModifier,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun SpeedDialActionItem(action: SpeedDialAction, onClick: () -> Unit) {
    Row(
        // Aligns the small button with the center of the main one.
        modifier = Modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Touching the label works too, but only the button is on the D-pad focus path.
        Surface(
            modifier = Modifier
                .padding(end = 16.dp)
                .clip(MaterialTheme.shapes.small)
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = action.label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        // In the colors of the app bar, which stand out more than the secondary ones.
        val appColors = LocalAppColors.current
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = appColors.appBarContainer,
            contentColor = appColors.onAppBar
        ) {
            Icon(painter = painterResource(action.iconRes), contentDescription = null)
        }
    }
}

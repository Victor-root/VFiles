/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.colorpicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R

/**
 * Picks one of [colors] by index. When [hasLeadingDynamicColor], the first color is the one based
 * on the wallpaper and is shown apart from the others.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    colors: List<Color>,
    hasLeadingDynamicColor: Boolean,
    selectedIndex: Int,
    defaultIndex: Int,
    onColorSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var checkedIndex by rememberSaveable { mutableIntStateOf(selectedIndex) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onColorSelected(checkedIndex)
                }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { checkedIndex = defaultIndex }) {
                    Text(stringResource(R.string.default_))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val firstPresetIndex = if (hasLeadingDynamicColor) 1 else 0
                if (hasLeadingDynamicColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.color_picker_dynamic_color_label),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        ColorSwatch(colors[0], checkedIndex == 0) { checkedIndex = 0 }
                    }
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.color_picker_preset_colors_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (index in firstPresetIndex..colors.lastIndex) {
                        ColorSwatch(colors[index], checkedIndex == index) {
                            checkedIndex = index
                        }
                    }
                }
            }
        }
    )
}

// The checked color gets a ring of the theme color around it, apart from it, and a check mark.
@Composable
private fun ColorSwatch(color: Color, isChecked: Boolean, onClick: () -> Unit) {
    val ringColor by animateColorAsState(
        if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent
    )
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .selectable(selected = isChecked, role = Role.RadioButton, onClick = onClick)
            .border(SWATCH_RING_WIDTH, ringColor, CircleShape)
            .padding(SWATCH_RING_WIDTH + SWATCH_RING_GAP)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = isChecked, enter = fadeIn(), exit = fadeOut()) {
            Icon(
                painter = painterResource(R.drawable.check_icon_white_24dp),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White
            )
        }
    }
}

private val SWATCH_SIZE = 52.dp
private val SWATCH_RING_WIDTH = 3.dp
private val SWATCH_RING_GAP = 3.dp

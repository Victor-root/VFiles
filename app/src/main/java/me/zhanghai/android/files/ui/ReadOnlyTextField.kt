/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * A text field showing a value that is changed elsewhere, by clicking the field. The text field
 * itself is disabled and covered by the clickable area, so that the D-pad stops on it only once.
 */
@Composable
fun ReadOnlyTextField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val colorScheme = MaterialTheme.colorScheme
        OutlinedTextField(
            state = remember(value) { TextFieldState(value) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
            enabled = false,
            label = { Text(label) },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = colorScheme.onSurface,
                disabledBorderColor = colorScheme.outline,
                disabledLabelColor = colorScheme.onSurfaceVariant
            )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                // The outline starts below the top half of the label.
                .padding(top = 8.dp)
                .clip(OutlinedTextFieldDefaults.shape)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = label
                    text = AnnotatedString(value)
                }
        )
    }
}

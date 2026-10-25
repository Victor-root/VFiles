/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun ConfirmationDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    title: String? = null,
    confirmText: String = stringResource(android.R.string.ok),
    dismissText: String = stringResource(android.R.string.cancel)
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                }
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(dismissText) } },
        title = title?.let { { Text(it) } },
        text = { Text(text) }
    )
}

/**
 * A dialog asking for a line of text, with the keyboard shown. [validate] returns an error message
 * that keeps the dialog open, or null to confirm the text, which is trimmed.
 */
@Composable
fun TextFieldDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    initialText: String = "",
    initialSelection: TextRange = TextRange(0, initialText.length),
    label: String? = null,
    validate: (String) -> String? = { null },
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    extraContent: (@Composable () -> Unit)? = null
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, initialSelection))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val confirm = {
        val text = value.text.trim()
        val validationError = validate(text)
        if (validationError != null) {
            error = validationError
        } else {
            onDismissRequest()
            onConfirm(text)
        }
    }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = confirm) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = label?.let { { Text(it) } },
                    supportingText = error?.let { { Text(it) } },
                    isError = error != null,
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    singleLine = true
                )
                extraContent?.invoke()
            }
        }
    )
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

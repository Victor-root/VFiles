/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.flow.drop

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
 * that keeps the dialog open, or null to confirm the text, which is trimmed unless [trim] is false.
 * [neutralButton] goes before the cancel button.
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
    outputTransformation: OutputTransformation? = null,
    extraContent: (@Composable () -> Unit)? = null,
    placeholder: String? = null,
    trim: Boolean = true,
    neutralButton: (@Composable () -> Unit)? = null
) {
    val state = rememberTextFieldState(initialText, initialSelection)
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state) { snapshotFlow { state.text }.drop(1).collect { error = null } }
    val confirm = {
        val text = if (trim) state.text.trim().toString() else state.text.toString()
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
            Row {
                neutralButton?.invoke()
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
        title = { Text(title) },
        text = {
            // The extra content may not fit with the keyboard shown.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = label?.let { { Text(it) } },
                    placeholder = placeholder?.let { { Text(it) } },
                    supportingText = error?.let { { Text(it) } },
                    isError = error != null,
                    outputTransformation = outputTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Done
                    ),
                    onKeyboardAction = { confirm() },
                    lineLimits = TextFieldLineLimits.SingleLine
                )
                extraContent?.invoke()
            }
        }
    )
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

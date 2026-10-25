/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.flow.drop
import me.zhanghai.android.files.R

/** The text of a form field, with an error shown under it until its text changes. */
@Stable
class FormFieldState(val textFieldState: TextFieldState, @StringRes errorRes: Int? = null) {
    @get:StringRes
    var errorRes: Int? by mutableStateOf(errorRes)

    val focusRequester = FocusRequester()

    val text: String
        get() = textFieldState.text.toString()

    companion object {
        val Saver: Saver<FormFieldState, Any> = listSaver(
            save = { listOf(with(TextFieldState.Saver) { save(it.textFieldState) }, it.errorRes) },
            restore = {
                FormFieldState(TextFieldState.Saver.restore(it[0]!!)!!, it[1] as Int?)
            }
        )
    }
}

@Composable
fun rememberFormFieldState(
    initialText: String = "",
    initialSelection: TextRange = TextRange(initialText.length)
): FormFieldState =
    rememberSaveable(saver = FormFieldState.Saver) {
        FormFieldState(TextFieldState(initialText, initialSelection))
    }

/** Collects the errors of a form while validating it. */
class FormErrors {
    private var firstErrorField: FormFieldState? = null

    fun set(field: FormFieldState, @StringRes errorRes: Int) {
        field.errorRes = errorRes
        if (firstErrorField == null) {
            firstErrorField = field
        }
    }

    /** Returns whether the form is valid, otherwise focuses the first field with an error. */
    fun check(): Boolean {
        val field = firstErrorField ?: return true
        field.focusRequester.requestFocus()
        return false
    }
}

/**
 * A text field of a form. The label stays above the field when there's a [placeholder], so that
 * the placeholder is always shown when the field is empty.
 */
@Composable
fun FormTextField(
    field: FormFieldState,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isMultiLine: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
    onTextChange: () -> Unit = { field.errorRes = null }
) {
    TextChangeEffect(field, onTextChange)
    OutlinedTextField(
        state = field.textFieldState,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(field.focusRequester),
        labelPosition = labelPosition(placeholder),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        supportingText = field.errorRes?.let { { Text(stringResource(it)) } },
        isError = field.errorRes != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        lineLimits = if (isMultiLine) {
            TextFieldLineLimits.MultiLine()
        } else {
            TextFieldLineLimits.SingleLine
        }
    )
}

/** A password field of a form, with a button to show the password. */
@Composable
fun FormPasswordField(
    field: FormFieldState,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onKeyboardAction: KeyboardActionHandler? = null,
    onTextChange: () -> Unit = { field.errorRes = null }
) {
    TextChangeEffect(field, onTextChange)
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = field.textFieldState,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(field.focusRequester),
        labelPosition = labelPosition(placeholder),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                Icon(
                    painter = painterResource(
                        if (isPasswordVisible) {
                            R.drawable.visibility_off_icon_white_24dp
                        } else {
                            R.drawable.visibility_icon_white_24dp
                        }
                    ),
                    contentDescription = stringResource(
                        if (isPasswordVisible) R.string.hide else R.string.show
                    )
                )
            }
        },
        supportingText = field.errorRes?.let { { Text(stringResource(it)) } },
        isError = field.errorRes != null,
        onKeyboardAction = onKeyboardAction,
        textObfuscationMode = if (isPasswordVisible) {
            TextObfuscationMode.Visible
        } else {
            TextObfuscationMode.RevealLastTyped
        }
    )
}

/** A read-only text field, whose text can still be focused and selected. */
@Composable
fun ReadOnlyFormTextField(text: String, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        state = remember(text) { TextFieldState(text) },
        modifier = modifier.fillMaxWidth(),
        readOnly = true,
        label = { Text(label) }
    )
}

/** A choice among a few entries, shown as a menu under the field. */
@Composable
fun <T> FormDropdownField(
    label: String,
    entries: List<T>,
    entryLabel: (T) -> String,
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = entryLabel(value),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            singleLine = true
        )
        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            for (entry in entries) {
                DropdownMenuItem(
                    text = { Text(entryLabel(entry)) },
                    onClick = {
                        isExpanded = false
                        onValueChange(entry)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun TextChangeEffect(field: FormFieldState, onTextChange: () -> Unit) {
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    LaunchedEffect(field) {
        // The first value is the current text.
        snapshotFlow { field.text }.drop(1).collect { currentOnTextChange() }
    }
}

private fun labelPosition(placeholder: String?): TextFieldLabelPosition =
    TextFieldLabelPosition.Attached(alwaysMinimize = placeholder != null)

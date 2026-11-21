/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import me.zhanghai.android.files.util.isTelevision

/** The [modifier] and the [keyboardOptions] of an editable text field. */
@Immutable
class TextFieldKeyboard(val modifier: Modifier, val keyboardOptions: KeyboardOptions)

/**
 * The keyboard of an editable text field. On a TV, it doesn't open as soon as the D-pad focuses the
 * field, but once the field is clicked with the center key of the remote.
 */
@Composable
fun rememberTextFieldKeyboard(
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
): TextFieldKeyboard {
    val context = LocalContext.current
    val isTelevision = remember(context) { context.isTelevision }
    var isRequested by remember { mutableStateOf(false) }
    if (!isTelevision) {
        return remember(keyboardOptions) { TextFieldKeyboard(Modifier, keyboardOptions) }
    }
    val modifier = remember {
        Modifier
            .onFocusChanged {
                if (!it.hasFocus) {
                    isRequested = false
                }
            }
            .onPreviewKeyEvent {
                // Once requested, the field opens the keyboard again by itself with the key.
                val isRequest = !isRequested && it.type == KeyEventType.KeyDown &&
                    it.key == Key.DirectionCenter
                if (isRequest) {
                    isRequested = true
                }
                isRequest
            }
    }
    // Opening it once focused starts the input of the field, which doesn't happen otherwise.
    return TextFieldKeyboard(
        modifier,
        if (isRequested) keyboardOptions else keyboardOptions.copy(showKeyboardOnFocus = false)
    )
}

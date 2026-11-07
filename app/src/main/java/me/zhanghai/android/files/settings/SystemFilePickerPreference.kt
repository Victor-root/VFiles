/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.provider.root.ShizukuFileServiceLauncher
import me.zhanghai.android.files.ui.SwitchPreference
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import java.io.IOException

@Composable
fun SystemFilePickerPreference() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // The state comes from the system, since it can also be changed with adb.
    var refreshCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshCount++ }
    val hasPicker = remember(refreshCount) { SystemFilePicker.isAvailable }
    val isDisabled = remember(refreshCount) { SystemFilePicker.isDisabled }
    val isShizukuAvailable by ShizukuFileServiceLauncher.isAvailableFlow
        .collectAsStateWithLifecycle(ShizukuFileServiceLauncher.isAvailable())
    var isChanging by remember { mutableStateOf(false) }
    val setDisabled = { disabled: Boolean ->
        isChanging = true
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { SystemFilePicker.setDisabled(disabled) }
            } catch (e: IOException) {
                e.printStackTrace()
                context.showToast(R.string.settings_system_file_picker_error)
            }
            isChanging = false
            refreshCount++
        }
        Unit
    }
    var isConfirmationShown by rememberSaveable { mutableStateOf(false) }
    var isShizukuMessageShown by rememberSaveable { mutableStateOf(false) }
    SwitchPreference(
        title = stringResource(R.string.settings_system_file_picker_title),
        checked = isDisabled,
        onCheckedChange = {
            // Not disabled meanwhile, which would take the focus away from it on a D-pad.
            when {
                isChanging -> {}
                !isShizukuAvailable -> isShizukuMessageShown = true
                it -> isConfirmationShown = true
                else -> setDisabled(false)
            }
        },
        summary = when {
            !hasPicker -> stringResource(R.string.settings_system_file_picker_not_found)
            isShizukuAvailable ->
                stringResource(R.string.settings_system_file_picker_summary) + "\n" +
                    stringResource(R.string.settings_system_file_picker_shizuku_required)
            else -> stringResource(R.string.settings_system_file_picker_summary)
        },
        enabled = hasPicker,
        icon = painterResource(R.drawable.apps_icon_white_24dp),
        warning = if (hasPicker && !isShizukuAvailable) {
            stringResource(R.string.settings_system_file_picker_shizuku_missing)
        } else {
            null
        }
    )
    if (isConfirmationShown) {
        ConfirmationDialog(
            onConfirm = { setDisabled(true) },
            onDismissRequest = { isConfirmationShown = false }
        )
    }
    if (isShizukuMessageShown) {
        val dismiss = { isShizukuMessageShown = false }
        AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = {
                TextButton(onClick = dismiss) { Text(stringResource(android.R.string.ok)) }
            },
            title = { Text(stringResource(R.string.settings_system_file_picker_shizuku_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.settings_system_file_picker_shizuku_message))
                    Command(
                        if (isDisabled) {
                            SystemFilePicker.enableCommand
                        } else {
                            SystemFilePicker.disableCommand
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun ConfirmationDialog(onConfirm: () -> Unit, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                }
            ) {
                Text(stringResource(R.string.settings_system_file_picker_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.settings_system_file_picker_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.settings_system_file_picker_confirm_message))
                Text(stringResource(R.string.settings_system_file_picker_confirm_note))
                // Last, so that moving to its buttons with a D-pad reveals all of the text.
                ImportantCard()
            }
        }
    )
}

@Composable
private fun ImportantCard() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.settings_system_file_picker_confirm_important_title
                ),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.settings_system_file_picker_confirm_important))
            Command(
                command = SystemFilePicker.enableCommand,
                buttonColors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    }
}

/** A command to run from a computer, to copy or to send there. */
@Composable
private fun Command(
    command: String,
    buttonColors: ButtonColors = ButtonDefaults.textButtonColors()
) {
    val context = LocalContext.current
    Column {
        SelectionContainer {
            Text(
                text = command,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row {
            TextButton(
                onClick = { clipboardManager.copyText(command, context) },
                colors = buttonColors
            ) {
                Icon(
                    painter = painterResource(R.drawable.copy_icon_control_normal_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.copy))
            }
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType(MimeType.TEXT_PLAIN.value)
                        .putExtra(Intent.EXTRA_TEXT, command)
                    context.startActivitySafe(intent.withChooser())
                },
                colors = buttonColors
            ) {
                Icon(
                    painter = painterResource(R.drawable.share_icon_control_normal_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.share))
            }
        }
    }
}

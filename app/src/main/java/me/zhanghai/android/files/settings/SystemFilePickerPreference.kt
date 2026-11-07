/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.provider.root.ShizukuFileServiceLauncher
import me.zhanghai.android.files.ui.ConfirmationDialog
import me.zhanghai.android.files.ui.SwitchPreference
import me.zhanghai.android.files.util.showToast
import java.io.IOException

@Composable
fun SystemFilePickerPreference() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // The state comes from the system, since it can also be changed with adb.
    var refreshCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshCount++ }
    val hasPicker = remember(refreshCount) { SystemFilePicker.packageNames.isNotEmpty() }
    val isDisabled = remember(refreshCount) { SystemFilePicker.isDisabled }
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
                !ShizukuFileServiceLauncher.isAvailable() -> isShizukuMessageShown = true
                it -> isConfirmationShown = true
                else -> setDisabled(false)
            }
        },
        summary = if (hasPicker) {
            stringResource(R.string.settings_system_file_picker_summary)
        } else {
            stringResource(R.string.settings_system_file_picker_not_found)
        } + debugFilePickers,
        enabled = hasPicker,
        icon = painterResource(R.drawable.apps_icon_white_24dp)
    )
    if (isConfirmationShown) {
        ConfirmationDialog(
            text = stringResource(
                R.string.settings_system_file_picker_confirm_message,
                SystemFilePicker.enableCommand
            ),
            onConfirm = { setDisabled(true) },
            onDismissRequest = { isConfirmationShown = false },
            title = stringResource(R.string.settings_system_file_picker_confirm_title),
            confirmText = stringResource(R.string.settings_system_file_picker_confirm_action)
        )
    }
    if (isShizukuMessageShown) {
        ConfirmationDialog(
            text = stringResource(
                R.string.settings_system_file_picker_shizuku_message,
                if (isDisabled) SystemFilePicker.enableCommand else SystemFilePicker.disableCommand
            ),
            onConfirm = {},
            onDismissRequest = { isShizukuMessageShown = false },
            title = stringResource(R.string.settings_system_file_picker_shizuku_title)
        )
    }
}

// Temporary, to find out what answers on devices where the file picker isn't found.
private val debugFilePickers: String
    get() {
        if (BuildConfig.BUILD_TYPE != "debug") {
            return ""
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        val handlers = packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.activityInfo.packageName }
            .distinct()
        return "\n[debug] ${SystemFilePicker.packageNames} / $handlers"
    }

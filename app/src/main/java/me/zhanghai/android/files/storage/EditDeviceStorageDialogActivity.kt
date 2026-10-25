/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.ui.FormTextField
import me.zhanghai.android.files.ui.ReadOnlyFormTextField
import me.zhanghai.android.files.ui.rememberFormFieldState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args

class EditDeviceStorageDialogActivity : AppActivity() {
    private val args by args<Args>()

    @Composable
    override fun Content() {
        EditDeviceStorageDialog(args.deviceStorage, onDismissRequest = ::finish)
    }

    @Parcelize
    class Args(val deviceStorage: DeviceStorage) : ParcelableArgs
}

@Composable
private fun EditDeviceStorageDialog(deviceStorage: DeviceStorage, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val defaultName = deviceStorage.getDefaultName(context)
    val name = deviceStorage.getName(context)
    val nameField = rememberFormFieldState(name, TextRange(0, name.length))
    EditStorageDialog(
        title = stringResource(R.string.storage_edit_device_storage_title),
        initialFocusRequester = nameField.focusRequester,
        onConfirm = {
            val customName = nameField.text.takeIf { it.isNotEmpty() && it != defaultName }
            Storages.replace(deviceStorage.copy_(customName = customName))
            onDismissRequest()
        },
        neutralText = stringResource(if (deviceStorage.isVisible) R.string.hide else R.string.show),
        onNeutral = {
            Storages.replace(deviceStorage.copy_(isVisible = !deviceStorage.isVisible))
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest
    ) {
        FormTextField(
            field = nameField,
            label = stringResource(R.string.storage_edit_device_storage_name),
            placeholder = defaultName
        )
        ReadOnlyFormTextField(
            text = deviceStorage.linuxPath,
            label = stringResource(R.string.storage_edit_device_storage_path)
        )
    }
}

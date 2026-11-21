/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.file.ExternalStorageUri
import me.zhanghai.android.files.file.displayName
import me.zhanghai.android.files.ui.FormErrors
import me.zhanghai.android.files.ui.FormTextField
import me.zhanghai.android.files.ui.rememberFormFieldState
import me.zhanghai.android.files.util.takeIfNotEmpty

@Parcelize
data class EditExternalStorageShortcutDestination(
    val externalStorageShortcut: ExternalStorageShortcut
) : Destination {
    override val isDialog: Boolean
        get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        EditExternalStorageShortcutDialog(
            externalStorageShortcut, onDismissRequest = navigator::goBack
        )
    }
}

@Composable
private fun EditExternalStorageShortcutDialog(
    externalStorageShortcut: ExternalStorageShortcut,
    onDismissRequest: () -> Unit
) {
    val name = externalStorageShortcut.getName(LocalContext.current)
    val nameField = rememberFormFieldState(name, TextRange(0, name.length))
    val uri = externalStorageShortcut.uri
    val rootIdField = rememberFormFieldState(uri.rootId)
    val pathField = rememberFormFieldState(uri.path)
    val path = pathField.text.dropWhile { it == '/' }
    val namePlaceholder = ExternalStorageUri(
        rootIdField.text.takeIfNotEmpty()
            ?: DocumentsContractCompat.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID,
        path
    ).displayName
    EditStorageDialog(
        title = stringResource(R.string.storage_edit_external_storage_shortcut_title),
        initialFocusRequester = nameField.focusRequester,
        onConfirm = {
            val errors = FormErrors()
            val customName = nameField.text.takeIf { it.isNotEmpty() && it != namePlaceholder }
            val rootId = rootIdField.text.takeIfNotEmpty()
            if (rootId == null) {
                errors.set(
                    rootIdField, R.string.storage_edit_external_storage_shortcut_root_id_error_empty
                )
            }
            if (errors.check()) {
                Storages.replace(
                    ExternalStorageShortcut(
                        externalStorageShortcut.id, customName, ExternalStorageUri(rootId!!, path)
                    )
                )
                onDismissRequest()
            }
        },
        neutralText = stringResource(R.string.remove),
        onNeutral = {
            Storages.remove(externalStorageShortcut)
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest
    ) {
        FormTextField(
            field = nameField,
            label = stringResource(R.string.storage_edit_document_tree_name),
            placeholder = namePlaceholder
        )
        FormTextField(
            field = rootIdField,
            label = stringResource(R.string.storage_edit_external_storage_shortcut_root_id)
        )
        FormTextField(
            field = pathField,
            label = stringResource(R.string.storage_edit_external_storage_shortcut_path),
            keyboardType = KeyboardType.Uri
        )
    }
}

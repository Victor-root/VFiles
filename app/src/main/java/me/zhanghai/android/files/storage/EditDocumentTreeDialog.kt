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
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.ui.FormTextField
import me.zhanghai.android.files.ui.ReadOnlyFormTextField
import me.zhanghai.android.files.ui.rememberFormFieldState

@Parcelize
data class EditDocumentTreeDestination(val documentTree: DocumentTree) : Destination {
    override val isDialog: Boolean
        get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        EditDocumentTreeDialog(documentTree, onDismissRequest = navigator::goBack)
    }
}

@Composable
private fun EditDocumentTreeDialog(documentTree: DocumentTree, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val defaultName = documentTree.getDefaultName(context)
    val name = documentTree.getName(context)
    val nameField = rememberFormFieldState(name, TextRange(0, name.length))
    EditStorageDialog(
        title = stringResource(R.string.storage_edit_document_tree_title),
        initialFocusRequester = nameField.focusRequester,
        onConfirm = {
            val customName = nameField.text.takeIf { it.isNotEmpty() && it != defaultName }
            Storages.replace(documentTree.copy(customName = customName))
            onDismissRequest()
        },
        neutralText = stringResource(R.string.remove),
        onNeutral = {
            Storages.remove(documentTree)
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest
    ) {
        FormTextField(
            field = nameField,
            label = stringResource(R.string.storage_edit_document_tree_name),
            placeholder = defaultName
        )
        ReadOnlyFormTextField(
            text = documentTree.uri.value.toString(),
            label = stringResource(R.string.storage_edit_document_tree_uri)
        )
        val linuxPath = documentTree.linuxPath
        if (linuxPath != null) {
            ReadOnlyFormTextField(
                text = linuxPath,
                label = stringResource(R.string.storage_edit_document_tree_path)
            )
        }
    }
}

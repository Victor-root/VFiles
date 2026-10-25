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
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.ui.FormTextField
import me.zhanghai.android.files.ui.ReadOnlyFormTextField
import me.zhanghai.android.files.ui.rememberFormFieldState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args

class EditDocumentTreeDialogActivity : ComposeActivity() {
    private val args by args<Args>()

    @Composable
    override fun Content() {
        EditDocumentTreeDialog(args.documentTree, onDismissRequest = ::finish)
    }

    @Parcelize
    class Args(val documentTree: DocumentTree) : ParcelableArgs
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

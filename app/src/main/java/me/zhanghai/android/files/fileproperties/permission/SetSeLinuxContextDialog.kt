/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.permission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.fileproperties.CheckboxItem
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.storage.FormTextField
import me.zhanghai.android.files.storage.rememberFormFieldState

/** Changes or restores the SELinux context of [file], with the keyboard shown. */
@Composable
fun SetSeLinuxContextDialog(file: FileItem, onDismissRequest: () -> Unit) {
    val fileSeLinuxContext =
        (file.attributes as PosixFileAttributes).seLinuxContext()?.toString().orEmpty()
    val field = rememberFormFieldState(fileSeLinuxContext)
    var isRecursive by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    val seLinuxContext = field.text
                    if (isRecursive || seLinuxContext != fileSeLinuxContext) {
                        FileJobService.setSeLinuxContext(
                            file.path, seLinuxContext, isRecursive, context
                        )
                    }
                }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        FileJobService.restoreSeLinuxContext(file.path, isRecursive, context)
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.file_properties_permission_set_selinux_context_restore
                        )
                    )
                }
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
        title = {
            Text(stringResource(R.string.file_properties_permission_set_selinux_context_title))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FormTextField(
                    field = field,
                    label = stringResource(R.string.file_properties_permission_selinux_context)
                )
                if (file.attributes.isDirectory) {
                    CheckboxItem(
                        text = stringResource(R.string.file_properties_permission_recursive),
                        checked = isRecursive,
                        onCheckedChange = { isRecursive = it },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
    LaunchedEffect(field) { field.focusRequester.requestFocus() }
}

/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.apk

import android.content.pm.PermissionInfo
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.protectionCompat
import me.zhanghai.android.files.fileproperties.StatefulList
import me.zhanghai.android.files.fileproperties.ViewModelStoreScope
import me.zhanghai.android.files.ui.TextButton
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.observeAsStateCompat
import java.util.Locale

/** The permissions requested by an APK, whose names are copied on long click. */
@Composable
fun PermissionListDialog(permissionNames: Array<String>, onDismissRequest: () -> Unit) {
    ViewModelStoreScope {
        val viewModel = viewModel { PermissionListViewModel(permissionNames) }
        val permissionListStateful by viewModel.permissionListLiveData.observeAsStateCompat()
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.file_properties_apk_requested_permissions_positive_format,
                        permissionNames.size, permissionNames.size
                    )
                )
            },
            text = {
                StatefulList(stateful = permissionListStateful, key = { it.name }) {
                    PermissionListItem(it)
                }
            }
        )
    }
}

@Composable
private fun PermissionListItem(permission: PermissionItem) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { clipboardManager.copyText(permission.name, context) }
            )
            .padding(vertical = 8.dp)
    ) {
        val label = permission.label
        Text(
            text = label?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            } ?: permission.name,
            fontWeight = if (
                permission.permissionInfo?.protectionCompat == PermissionInfo.PROTECTION_DANGEROUS
            ) {
                FontWeight.Bold
            } else {
                null
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (label != null) {
            Text(
                text = permission.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (permission.description != null) {
            Text(
                text = permission.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

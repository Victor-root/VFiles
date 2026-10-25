/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import me.zhanghai.android.files.R
import me.zhanghai.android.files.fileproperties.FilePropertiesFileViewModel
import me.zhanghai.android.files.fileproperties.FilePropertiesItem
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.PosixPrincipal
import me.zhanghai.android.files.provider.common.toInt
import me.zhanghai.android.files.provider.common.toModeString
import me.zhanghai.android.files.util.observeAsStateCompat

private enum class PermissionDialog {
    OWNER,
    GROUP,
    MODE,
    SELINUX_CONTEXT
}

@Composable
fun FilePropertiesPermissionTab(viewModel: FilePropertiesFileViewModel) {
    val stateful by viewModel.fileLiveData.observeAsStateCompat()
    var dialog by rememberSaveable { mutableStateOf<PermissionDialog?>(null) }
    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { file ->
        val attributes = file.attributes as PosixFileAttributes
        val owner = attributes.owner()
        FilePropertiesItem(
            R.string.file_properties_permission_owner, getPrincipalText(owner),
            owner?.let { { dialog = PermissionDialog.OWNER } }
        )
        val group = attributes.group()
        FilePropertiesItem(
            R.string.file_properties_permission_group, getPrincipalText(group),
            group?.let { { dialog = PermissionDialog.GROUP } }
        )
        val mode = attributes.mode()
        FilePropertiesItem(
            R.string.file_properties_permission_mode,
            if (mode != null) {
                stringResource(
                    R.string.file_properties_permission_mode_format, mode.toModeString(),
                    mode.toInt()
                )
            } else {
                stringResource(R.string.unknown)
            },
            if (mode != null && !attributes.isSymbolicLink) {
                { dialog = PermissionDialog.MODE }
            } else {
                null
            }
        )
        val seLinuxContext = attributes.seLinuxContext()
        if (seLinuxContext != null) {
            FilePropertiesItem(
                R.string.file_properties_permission_selinux_context,
                if (seLinuxContext.isNotEmpty()) {
                    seLinuxContext.toString()
                } else {
                    stringResource(R.string.empty_placeholder)
                }
            ) { dialog = PermissionDialog.SELINUX_CONTEXT }
        }
    }
    val file = stateful.value ?: return
    val onDismissRequest = { dialog = null }
    when (dialog) {
        PermissionDialog.OWNER -> SetOwnerDialog(file, onDismissRequest)
        PermissionDialog.GROUP -> SetGroupDialog(file, onDismissRequest)
        PermissionDialog.MODE -> SetModeDialog(file, onDismissRequest)
        PermissionDialog.SELINUX_CONTEXT -> SetSeLinuxContextDialog(file, onDismissRequest)
        null -> {}
    }
}

@Composable
private fun getPrincipalText(principal: PosixPrincipal?): String =
    if (principal != null) {
        getPrincipalText(principal.id, principal.name)
    } else {
        stringResource(R.string.unknown)
    }

@Composable
internal fun getPrincipalText(id: Int, name: String?): String =
    if (name != null) {
        stringResource(R.string.file_properties_permission_principal_format, name, id)
    } else {
        id.toString()
    }

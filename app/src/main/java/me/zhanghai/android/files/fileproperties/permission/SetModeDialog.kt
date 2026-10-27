/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.permission

import android.icu.text.ListFormatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.fileproperties.ViewModelStoreScope
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.ui.CheckboxItem
import me.zhanghai.android.files.util.observeAsStateCompat

@Composable
fun SetModeDialog(file: FileItem, onDismissRequest: () -> Unit) {
    ViewModelStoreScope {
        val fileMode = (file.attributes as PosixFileAttributes).mode()!!
        val viewModel = viewModel { SetModeViewModel(fileMode) }
        val mode by viewModel.modeLiveData.observeAsStateCompat()
        val isDirectory = file.attributes.isDirectory
        var isRecursive by rememberSaveable { mutableStateOf(false) }
        var isUppercaseX by rememberSaveable { mutableStateOf(true) }
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        if (isRecursive || mode != fileMode) {
                            FileJobService.setMode(
                                file.path, mode, isRecursive, isUppercaseX, context
                            )
                        }
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.file_properties_permission_set_mode_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val normalModeBitNames = stringArrayResource(
                        if (isDirectory) {
                            R.array.file_properties_permission_set_mode_normal_mode_bits_directory
                        } else {
                            R.array.file_properties_permission_set_mode_normal_mode_bits_file
                        }
                    )
                    val onToggleModeBit = viewModel::toggleModeBit
                    ModeBitsField(
                        R.string.file_properties_permission_owner, OWNER_MODE_BITS,
                        normalModeBitNames, mode, onToggleModeBit
                    )
                    ModeBitsField(
                        R.string.file_properties_permission_group, GROUP_MODE_BITS,
                        normalModeBitNames, mode, onToggleModeBit
                    )
                    ModeBitsField(
                        R.string.file_properties_permission_set_mode_others, OTHERS_MODE_BITS,
                        normalModeBitNames, mode, onToggleModeBit
                    )
                    ModeBitsField(
                        R.string.file_properties_permission_set_mode_special, SPECIAL_MODE_BITS,
                        stringArrayResource(
                            R.array.file_properties_permission_set_mode_special_mode_bits
                        ),
                        mode, onToggleModeBit
                    )
                    if (isDirectory) {
                        Column {
                            CheckboxItem(
                                text = stringResource(
                                    R.string.file_properties_permission_recursive
                                ),
                                checked = isRecursive,
                                onCheckedChange = { isRecursive = it }
                            )
                            CheckboxItem(
                                text = stringResource(
                                    R.string.file_properties_permission_set_mode_uppercase_x
                                ),
                                checked = isUppercaseX,
                                onCheckedChange = { isUppercaseX = it },
                                enabled = isRecursive
                            )
                        }
                    }
                }
            }
        )
    }
}

/** The checked [modeBits] of [mode], changed in a menu which stays open to change several. */
@Composable
private fun ModeBitsField(
    @StringRes labelRes: Int,
    modeBits: List<PosixFileModeBit>,
    modeBitNames: Array<String>,
    mode: Set<PosixFileModeBit>,
    onToggleModeBit: (PosixFileModeBit) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val checkedNames = modeBits.indices.filter { modeBits[it] in mode }.map { modeBitNames[it] }
    val text = if (checkedNames.isEmpty()) {
        stringResource(R.string.none)
    } else {
        ListFormatter.getInstance().format(checkedNames)
    }
    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = it }) {
        OutlinedTextField(
            state = remember(text) { TextFieldState(text) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(labelRes)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) }
        )
        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            modeBits.forEachIndexed { index, modeBit ->
                val isChecked = modeBit in mode
                DropdownMenuItem(
                    text = { Text(modeBitNames[index]) },
                    onClick = { onToggleModeBit(modeBit) },
                    modifier = Modifier.semantics {
                        toggleableState = ToggleableState(isChecked)
                    },
                    leadingIcon = { Checkbox(checked = isChecked, onCheckedChange = null) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

private val OWNER_MODE_BITS = listOf(
    PosixFileModeBit.OWNER_READ,
    PosixFileModeBit.OWNER_WRITE,
    PosixFileModeBit.OWNER_EXECUTE
)

private val GROUP_MODE_BITS = listOf(
    PosixFileModeBit.GROUP_READ,
    PosixFileModeBit.GROUP_WRITE,
    PosixFileModeBit.GROUP_EXECUTE
)

private val OTHERS_MODE_BITS = listOf(
    PosixFileModeBit.OTHERS_READ,
    PosixFileModeBit.OTHERS_WRITE,
    PosixFileModeBit.OTHERS_EXECUTE
)

private val SPECIAL_MODE_BITS = listOf(
    PosixFileModeBit.SET_USER_ID,
    PosixFileModeBit.SET_GROUP_ID,
    PosixFileModeBit.STICKY
)

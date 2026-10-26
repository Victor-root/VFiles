/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Parcelable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java8.nio.file.Path
import java8.nio.file.Paths
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.fileproperties.FilePropertiesDialog
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.createOrLog
import me.zhanghai.android.files.ui.ConfirmationDialog
import me.zhanghai.android.files.ui.FormPasswordField
import me.zhanghai.android.files.ui.TextButton
import me.zhanghai.android.files.ui.TextFieldDialog
import me.zhanghai.android.files.ui.rememberFormFieldState
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.asPathNameOrNull
import me.zhanghai.android.files.util.observeAsStateCompat
import me.zhanghai.android.files.util.takeIfNotEmpty
import java.net.URI

/** The dialogs of the file list, saved across recreations while they're shown. */
sealed interface FileListDialog : Parcelable {
    @Parcelize
    data object CreateFile : FileListDialog

    @Parcelize
    data object CreateDirectory : FileListDialog

    @Parcelize
    class RenameFile(val file: FileItem) : FileListDialog

    @Parcelize
    class ConfirmDeleteFiles(val files: FileItemSet) : FileListDialog

    @Parcelize
    class ConfirmReplaceFile(val file: FileItem) : FileListDialog

    @Parcelize
    class CreateArchive(val files: FileItemSet) : FileListDialog

    @Parcelize
    class NavigateToPath(val path: @WriteWith<ParcelableParceler> Path) : FileListDialog

    @Parcelize
    class OpenApk(val file: FileItem) : FileListDialog

    @Parcelize
    class FileProperties(val file: FileItem) : FileListDialog

    @Parcelize
    data object AllFilesAccessRationale : FileListDialog

    @Parcelize
    data object StoragePermissionRationale : FileListDialog

    @Parcelize
    data object StoragePermissionInSettingsRationale : FileListDialog
}

@Composable
fun FileListDialogHost(
    dialog: FileListDialog,
    controller: FileListController,
    onDismissRequest: () -> Unit
) {
    when (dialog) {
        FileListDialog.CreateFile ->
            FileNameDialog(
                title = stringResource(R.string.file_create_file_title),
                hasFileWithName = controller::hasFileWithName,
                onConfirm = controller::createFile,
                onDismissRequest = onDismissRequest
            )
        FileListDialog.CreateDirectory ->
            FileNameDialog(
                title = stringResource(R.string.file_create_directory_title),
                hasFileWithName = controller::hasFileWithName,
                onConfirm = controller::createDirectory,
                onDismissRequest = onDismissRequest
            )
        is FileListDialog.RenameFile ->
            FileNameDialog(
                title = stringResource(R.string.rename),
                hasFileWithName = controller::hasFileWithName,
                onConfirm = { controller.renameFile(dialog.file, it) },
                onDismissRequest = onDismissRequest,
                initialText = dialog.file.name,
                initialSelection = TextRange(0, dialog.file.baseName.length),
                unchangedName = dialog.file.name
            )
        is FileListDialog.ConfirmDeleteFiles ->
            ConfirmationDialog(
                text = deleteFilesMessage(dialog.files),
                onConfirm = { controller.deleteFiles(dialog.files) },
                onDismissRequest = onDismissRequest
            )
        is FileListDialog.ConfirmReplaceFile ->
            ConfirmationDialog(
                text = stringResource(R.string.file_replace_message_format, dialog.file.name),
                onConfirm = { controller.replaceFile(dialog.file) },
                onDismissRequest = onDismissRequest
            )
        is FileListDialog.CreateArchive ->
            CreateArchiveDialog(
                files = dialog.files,
                hasFileWithName = controller::hasFileWithName,
                onConfirm = controller::archive,
                onDismissRequest = onDismissRequest
            )
        is FileListDialog.NavigateToPath ->
            NavigateToPathDialog(
                path = dialog.path,
                onConfirm = controller::navigateTo,
                onDismissRequest = onDismissRequest
            )
        is FileListDialog.OpenApk ->
            OpenApkDialog(
                onInstall = { controller.installApk(dialog.file) },
                onView = { controller.viewApk(dialog.file) },
                onDismissRequest = onDismissRequest
            )
        is FileListDialog.FileProperties ->
            FilePropertiesDialog(file = dialog.file, onDismissRequest = onDismissRequest)
        FileListDialog.AllFilesAccessRationale ->
            RationaleDialog(
                text = stringResource(R.string.all_files_access_rationale_message),
                confirmText = stringResource(android.R.string.ok),
                onResult = controller::onAllFilesAccessRationaleResult,
                onDismissRequest = onDismissRequest
            )
        FileListDialog.StoragePermissionRationale ->
            RationaleDialog(
                text = stringResource(R.string.storage_permission_rationale_message),
                confirmText = stringResource(android.R.string.ok),
                onResult = controller::onStoragePermissionRationaleResult,
                onDismissRequest = onDismissRequest
            )
        FileListDialog.StoragePermissionInSettingsRationale ->
            RationaleDialog(
                text = stringResource(R.string.storage_permission_permanently_denied_message),
                confirmText = stringResource(R.string.open_settings),
                onResult = controller::onStoragePermissionInSettingsRationaleResult,
                onDismissRequest = onDismissRequest
            )
    }
}

/**
 * Asks for the name of a new or renamed file, which is the text followed by [nameSuffix]. Keeping
 * [unchangedName] closes the dialog without doing anything.
 */
@Composable
private fun FileNameDialog(
    title: String,
    hasFileWithName: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    initialText: String = "",
    initialSelection: TextRange = TextRange(0, initialText.length),
    unchangedName: String? = null,
    nameSuffix: String = "",
    extraContent: (@Composable () -> Unit)? = null
) {
    val emptyError = stringResource(R.string.file_name_error_empty)
    val invalidError = stringResource(R.string.file_name_error_invalid)
    val existsError = stringResource(R.string.file_name_error_already_exists)
    TextFieldDialog(
        title = title,
        onConfirm = {
            val name = it + nameSuffix
            if (name != unchangedName) {
                onConfirm(name)
            }
        },
        onDismissRequest = onDismissRequest,
        initialText = initialText,
        initialSelection = initialSelection,
        validate = {
            val name = it + nameSuffix
            when {
                name == unchangedName -> null
                name.isEmpty() -> emptyError
                name.asFileNameOrNull() == null -> invalidError
                hasFileWithName(name) -> existsError
                else -> null
            }
        },
        extraContent = extraContent
    )
}

@Composable
private fun deleteFilesMessage(files: FileItemSet): String {
    if (files.size == 1) {
        val file = files.single()
        return stringResource(
            if (file.attributesNoFollowLinks.isDirectory) {
                R.string.file_delete_message_directory_format
            } else {
                R.string.file_delete_message_file_format
            },
            file.name
        )
    }
    val allDirectories = files.all { it.attributesNoFollowLinks.isDirectory }
    val allFiles = files.none { it.attributesNoFollowLinks.isDirectory }
    val messageRes = when {
        allDirectories -> R.plurals.file_delete_message_multiple_directories_format
        allFiles -> R.plurals.file_delete_message_multiple_files_format
        else -> R.plurals.file_delete_message_multiple_mixed_format
    }
    return pluralStringResource(messageRes, files.size, files.size)
}

/**
 * Asks for the name, type and optional password of a new archive. The name starts as the name of
 * the single file, or of the folder holding all the files.
 */
@Composable
private fun CreateArchiveDialog(
    files: FileItemSet,
    hasFileWithName: (String) -> Boolean,
    onConfirm: (FileItemSet, String, ArchiveType, String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val type by Settings.CREATE_ARCHIVE_TYPE.observeAsStateCompat()
    val passwordField = rememberFormFieldState()
    val initialName = remember(files) {
        files.singleOrNull()?.path?.fileName?.toString()
            ?: files.mapTo(mutableSetOf()) { it.path.parent }.singleOrNull()
                ?.takeIf { it.nameCount > 0 }?.fileName?.toString()
            ?: ""
    }
    FileNameDialog(
        title = stringResource(R.string.file_create_archive_title),
        hasFileWithName = hasFileWithName,
        onConfirm = { name ->
            val password =
                if (type.isPasswordSupported) passwordField.text.takeIfNotEmpty() else null
            onConfirm(files, name, type, password)
        },
        onDismissRequest = onDismissRequest,
        initialText = initialName,
        nameSuffix = ".${type.extension}",
        extraContent = {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .selectableGroup()
            ) {
                for (archiveType in ArchiveType.entries) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .selectable(
                                selected = archiveType == type,
                                onClick = { Settings.CREATE_ARCHIVE_TYPE.putValue(archiveType) },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = archiveType == type, onClick = null)
                        Text(
                            text = stringResource(archiveType.labelRes),
                            modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            AnimatedVisibility(visible = type.isPasswordSupported) {
                FormPasswordField(
                    field = passwordField,
                    label = stringResource(R.string.file_create_archive_password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    )
}

@Composable
private fun NavigateToPathDialog(
    path: Path,
    onConfirm: (Path) -> Unit,
    onDismissRequest: () -> Unit
) {
    val initialText = path.toUserFriendlyString()
    val emptyError = stringResource(R.string.file_list_path_error_empty)
    val invalidError = stringResource(R.string.file_list_path_error_invalid)
    TextFieldDialog(
        title = stringResource(R.string.file_list_navigate_to_title),
        onConfirm = {
            if (it != initialText) {
                onConfirm(it.toPathOrNull()!!)
            }
        },
        onDismissRequest = onDismissRequest,
        initialText = initialText,
        validate = {
            when {
                it == initialText -> null
                it.isEmpty() -> emptyError
                it.asPathNameOrNull() == null || it.toPathOrNull() == null -> invalidError
                else -> null
            }
        }
    )
}

// Accepts a URI, also in decoded form, or an absolute local path.
private fun String.toPathOrNull(): Path? {
    val uri = URI::class.createOrLog(this)
        ?: toUri().let {
            URI::class.createOrLog(
                it.scheme, it.userInfo, it.host, it.port, it.path, it.query, it.fragment
            )
        }
    if (uri != null) {
        try {
            return Paths.get(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (startsWith('/')) {
        return Paths.get(this)
    }
    return null
}

@Composable
private fun OpenApkDialog(onInstall: () -> Unit, onView: () -> Unit, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onInstall()
                }
            ) { Text(stringResource(R.string.install)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(android.R.string.cancel))
                }
                // Next to install, as both are the expected actions.
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onView()
                    }
                ) { Text(stringResource(R.string.view)) }
            }
        },
        text = { Text(stringResource(R.string.file_open_apk_message)) }
    )
}

/** Explains why a permission is needed, then reports whether to request it. */
@Composable
private fun RationaleDialog(
    text: String,
    confirmText: String,
    onResult: (Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    val dismiss = { shouldRequest: Boolean ->
        onDismissRequest()
        onResult(shouldRequest)
    }
    AlertDialog(
        onDismissRequest = { dismiss(false) },
        confirmButton = { TextButton(onClick = { dismiss(true) }) { Text(confirmText) } },
        dismissButton = {
            TextButton(onClick = { dismiss(false) }) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = { Text(text) }
    )
}

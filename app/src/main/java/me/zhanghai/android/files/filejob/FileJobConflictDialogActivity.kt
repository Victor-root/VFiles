/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filelist.FileItemIcon
import me.zhanghai.android.files.filelist.formatDescription
import me.zhanghai.android.files.ui.CheckboxItem
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.RemoteCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.getArgs
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.readParcelable

/**
 * Asks what to do with a file that already exists at the destination: merge or replace it, skip
 * it, rename the new one, or cancel, optionally for all such files.
 */
class FileJobConflictDialogActivity : ComposeActivity() {
    private val args by args<Args>()

    private var isListenerNotified = false

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val sourceFile = args.sourceFile
        val targetFile = args.targetFile
        val isMerge = isMerge(sourceFile, targetFile)
        val targetFileName = targetFile.path.fileName.toString()
        val nameState = rememberTextFieldState(
            targetFileName, TextRange(0, targetFileName.length)
        )
        val name = nameState.text.toString()
        val hasNewName = name.isNotEmpty() && name != targetFileName
        var isNameShown by rememberSaveable { mutableStateOf(false) }
        var isAll by rememberSaveable { mutableStateOf(false) }
        // Renaming only applies to this file.
        LaunchedEffect(hasNewName) {
            if (hasNewName) {
                isAll = false
            }
        }
        val dismiss = { action: FileJobConflictAction, isAllApplied: Boolean ->
            notifyListenerOnce(action, null, isAllApplied)
            finish()
        }
        AlertDialog(
            onDismissRequest = ::finish,
            confirmButton = {
                TextButton(
                    onClick = {
                        if (hasNewName) {
                            notifyListenerOnce(FileJobConflictAction.RENAME, name, false)
                            finish()
                        } else {
                            dismiss(FileJobConflictAction.MERGE_OR_REPLACE, isAll)
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            when {
                                hasNewName -> R.string.rename
                                isMerge -> R.string.merge
                                else -> R.string.replace
                            }
                        )
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { dismiss(FileJobConflictAction.CANCEL, false) }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(onClick = { dismiss(FileJobConflictAction.SKIP, isAll) }) {
                        Text(stringResource(R.string.skip))
                    }
                }
            },
            title = { Text(getTitle(sourceFile, targetFile, context)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(getMessage(sourceFile, targetFile, args.type, context))
                    ConflictFileRow(
                        file = targetFile,
                        label = stringResource(
                            if (isMerge) {
                                R.string.file_job_merge_target_name
                            } else {
                                R.string.file_job_replace_target_name
                            }
                        ),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    ConflictFileRow(
                        file = sourceFile,
                        label = stringResource(
                            if (isMerge) {
                                R.string.file_job_merge_source_name
                            } else {
                                R.string.file_job_replace_source_name
                            }
                        )
                    )
                    NewNameField(
                        isShown = isNameShown,
                        onShownChange = { isNameShown = it },
                        nameField = {
                            OutlinedTextField(
                                state = nameState,
                                modifier = it.fillMaxWidth(),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                trailingIcon = {
                                    IconButton(
                                        onClick = { nameState.setTextAndSelectAll(targetFileName) }
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                R.drawable.reset_icon_white_24dp
                                            ),
                                            contentDescription = stringResource(R.string.reset)
                                        )
                                    }
                                }
                            )
                        }
                    )
                    CheckboxItem(
                        text = stringResource(R.string.file_job_action_all),
                        checked = isAll,
                        onCheckedChange = { isAll = it },
                        enabled = !hasNewName
                    )
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false)
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            notifyListenerOnce(FileJobConflictAction.CANCELED, null, false)
        }
    }

    private fun notifyListenerOnce(action: FileJobConflictAction, name: String?, isAll: Boolean) {
        if (isListenerNotified) {
            return
        }
        args.listener(action, name, isAll)
        isListenerNotified = true
    }

    companion object {
        fun getTitle(sourceFile: FileItem, targetFile: FileItem, context: Context): String {
            val titleRes = if (isMerge(sourceFile, targetFile)) {
                R.string.file_job_merge_title_format
            } else {
                R.string.file_job_replace_title_format
            }
            return context.getString(titleRes, targetFile.path.fileName)
        }

        fun getMessage(
            sourceFile: FileItem,
            targetFile: FileItem,
            type: CopyMoveType,
            context: Context
        ): String {
            val messageRes = if (isMerge(sourceFile, targetFile)) {
                type.getResourceId(
                    R.string.file_job_merge_copy_message_format,
                    R.string.file_job_merge_extract_message_format,
                    R.string.file_job_merge_move_message_format
                )
            } else {
                R.string.file_job_replace_message_format
            }
            return context.getString(messageRes, targetFile.path.parent.fileName)
        }

        private fun isMerge(sourceFile: FileItem, targetFile: FileItem): Boolean {
            val sourceIsDirectory = sourceFile.attributesNoFollowLinks.isDirectory
            val targetIsDirectory = targetFile.attributesNoFollowLinks.isDirectory
            return sourceIsDirectory && targetIsDirectory
        }
    }

    @Parcelize
    class Args(
        val sourceFile: FileItem,
        val targetFile: FileItem,
        val type: CopyMoveType,
        val listener: @WriteWith<ListenerParceler>()
        (FileJobConflictAction, String?, Boolean) -> Unit
    ) : ParcelableArgs {
        object ListenerParceler : Parceler<(FileJobConflictAction, String?, Boolean) -> Unit> {
            override fun create(parcel: Parcel): (FileJobConflictAction, String?, Boolean) -> Unit =
                parcel.readParcelable<RemoteCallback>()!!.let {
                    { action, name, isAll ->
                        it.sendResult(Bundle().putArgs(ListenerArgs(action, name, isAll)))
                    }
                }

            override fun ((FileJobConflictAction, String?, Boolean) -> Unit).write(
                parcel: Parcel,
                flags: Int
            ) {
                parcel.writeParcelable(
                    RemoteCallback {
                        val args = it.getArgs<ListenerArgs>()
                        this(args.action, args.name, args.isAll)
                    }, flags
                )
            }

            @Parcelize
            private class ListenerArgs(
                val action: FileJobConflictAction,
                val name: String?,
                val isAll: Boolean
            ) : ParcelableArgs
        }
    }
}

@Composable
private fun ConflictFileRow(file: FileItem, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileItemIcon(file = file, modifier = Modifier.padding(end = 16.dp))
        Column {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = remember(file, context) { file.formatDescription(context) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// A row expanding into the field of a new name for the destination, focused when shown.
@Composable
private fun NewNameField(
    isShown: Boolean,
    onShownChange: (Boolean) -> Unit,
    nameField: @Composable (Modifier) -> Unit
) {
    val arrowRotation by animateFloatAsState(if (isShown) 90f else 0f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onShownChange(!isShown) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_end_icon_white_24dp),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 16.dp)
                .rotate(arrowRotation)
        )
        Text(
            text = stringResource(R.string.file_job_conflict_show_name),
            style = MaterialTheme.typography.bodyLarge
        )
    }
    AnimatedVisibility(visible = isShown) {
        val focusRequester = remember { FocusRequester() }
        nameField(Modifier.focusRequester(focusRequester))
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    }
}

/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.os.Bundle
import android.os.Parcel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.provider.common.PosixFileStore
import me.zhanghai.android.files.ui.CheckboxItem
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.RemoteCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.getArgs
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.isRunning
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.readParcelable
import me.zhanghai.android.files.util.showToast

/**
 * Tells about an error of a file job and asks what to do, optionally for all errors. A read-only
 * file store can be remounted as read-write from here.
 */
class FileJobErrorDialogActivity : ComposeActivity() {
    private val args by args<Args>()

    private var isListenerNotified = false

    @Composable
    override fun Content() {
        var isAll by rememberSaveable { mutableStateOf(false) }
        val dismiss = { action: FileJobErrorAction ->
            notifyListenerOnce(action, args.showAll && isAll)
            finish()
        }
        AlertDialog(
            onDismissRequest = ::finish,
            confirmButton = {
                args.positiveButtonText?.let {
                    TextButton(onClick = { dismiss(FileJobErrorAction.POSITIVE) }) {
                        Text(it.toString())
                    }
                }
            },
            dismissButton = {
                Row {
                    args.neutralButtonText?.let {
                        TextButton(onClick = { dismiss(FileJobErrorAction.NEUTRAL) }) {
                            Text(it.toString())
                        }
                    }
                    args.negativeButtonText?.let {
                        TextButton(onClick = { dismiss(FileJobErrorAction.NEGATIVE) }) {
                            Text(it.toString())
                        }
                    }
                }
            },
            title = { Text(args.title.toString()) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(args.message.toString())
                    args.readOnlyFileStore?.let { RemountButton(it) }
                    if (args.showAll) {
                        CheckboxItem(
                            text = stringResource(R.string.file_job_action_all),
                            checked = isAll,
                            onCheckedChange = { isAll = it }
                        )
                    }
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false)
        )
    }

    @Composable
    private fun RemountButton(fileStore: PosixFileStore) {
        val viewModel = viewModel { FileJobErrorViewModel() }
        val remountState by viewModel.remountState.collectAsStateWithLifecycle()
        LaunchedEffect(remountState) {
            when (val state = remountState) {
                is ActionState.Success -> viewModel.finishRemounting()
                is ActionState.Error -> {
                    val throwable = state.throwable
                    throwable.printStackTrace()
                    showToast(throwable.toString())
                    viewModel.finishRemounting()
                }
                is ActionState.Ready, is ActionState.Running -> {}
            }
        }
        val textRes = when {
            remountState.isRunning -> R.string.file_job_remount_loading_format
            fileStore.isReadOnly -> R.string.file_job_remount_format
            else -> R.string.file_job_remount_success_format
        }
        TextButton(
            onClick = {
                if (remountState.isReady && fileStore.isReadOnly) {
                    viewModel.remount(fileStore)
                }
            }
        ) { Text(stringResource(textRes, fileStore.name())) }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            notifyListenerOnce(FileJobErrorAction.CANCELED, false)
        }
    }

    private fun notifyListenerOnce(action: FileJobErrorAction, isAll: Boolean) {
        if (isListenerNotified) {
            return
        }
        args.listener(action, isAll)
        isListenerNotified = true
    }

    @Parcelize
    class Args(
        val title: CharSequence,
        val message: CharSequence,
        val readOnlyFileStore: @WriteWith<ParcelableParceler> PosixFileStore?,
        val showAll: Boolean,
        val positiveButtonText: CharSequence?,
        val negativeButtonText: CharSequence?,
        val neutralButtonText: CharSequence?,
        val listener: @WriteWith<ListenerParceler>() (FileJobErrorAction, Boolean) -> Unit
    ) : ParcelableArgs {
        object ListenerParceler : Parceler<(FileJobErrorAction, Boolean) -> Unit> {
            override fun create(parcel: Parcel): (FileJobErrorAction, Boolean) -> Unit =
                parcel.readParcelable<RemoteCallback>()!!.let {
                    { action, isAll ->
                        it.sendResult(Bundle().putArgs(ListenerArgs(action, isAll)))
                    }
                }

            override fun ((FileJobErrorAction, Boolean) -> Unit).write(parcel: Parcel, flags: Int) {
                parcel.writeParcelable(RemoteCallback {
                    val args = it.getArgs<ListenerArgs>()
                    this(args.action, args.isAll)
                }, flags)
            }

            @Parcelize
            private class ListenerArgs(
                val action: FileJobErrorAction,
                val isAll: Boolean
            ) : ParcelableArgs
        }
    }
}

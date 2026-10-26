/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileaction

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java8.nio.file.Path
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.provider.archive.archiveAddPassword
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.ui.FormPasswordField
import me.zhanghai.android.files.ui.TextButton
import me.zhanghai.android.files.ui.rememberFormFieldState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.RemoteCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.getArgs
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.readParcelable

/** Asks for the password of an archive, and tells whether it was given. */
class ArchivePasswordDialogActivity : AppActivity() {
    private val args by args<Args>()

    private var isListenerNotified = false

    @Composable
    override fun Content() {
        val passwordField = rememberFormFieldState()
        val confirm = {
            val password = passwordField.text
            if (password.isEmpty()) {
                passwordField.errorRes = R.string.file_action_archive_password_error_empty
            } else {
                args.path.archiveAddPassword(password)
                notifyListenerOnce(true)
                finish()
            }
        }
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = ::finish,
            confirmButton = {
                TextButton(onClick = confirm) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = ::finish) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(getTitle(context)) },
            text = {
                Column {
                    Text(getMessage(args.path.archiveFile, context))
                    FormPasswordField(
                        field = passwordField,
                        label = null,
                        modifier = Modifier.padding(top = 16.dp),
                        onKeyboardAction = { confirm() }
                    )
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false)
        )
        LaunchedEffect(passwordField) { passwordField.focusRequester.requestFocus() }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            notifyListenerOnce(false)
        }
    }

    private fun notifyListenerOnce(successful: Boolean) {
        if (isListenerNotified) {
            return
        }
        args.listener(successful)
        isListenerNotified = true
    }

    companion object {
        fun getTitle(context: Context): String =
            context.getString(R.string.file_action_archive_password_title)

        fun getMessage(archiveFile: Path, context: Context): String =
            context.getString(
                R.string.file_action_archive_password_message_format, archiveFile.fileName
            )
    }

    @Parcelize
    class Args(
        val path: @WriteWith<ParcelableParceler> Path,
        val listener: @WriteWith<ListenerParceler>()
        (Boolean) -> Unit
    ) : ParcelableArgs {
        object ListenerParceler : Parceler<(Boolean) -> Unit> {
            override fun create(parcel: Parcel): (Boolean) -> Unit =
                parcel.readParcelable<RemoteCallback>()!!.let {
                    { successful ->
                        it.sendResult(Bundle().putArgs(ListenerArgs(successful)))
                    }
                }

            override fun ((Boolean) -> Unit).write(parcel: Parcel, flags: Int) {
                parcel.writeParcelable(
                    RemoteCallback {
                        val args = it.getArgs<ListenerArgs>()
                        this(args.successful)
                    }, flags
                )
            }

            @Parcelize
            private class ListenerArgs(
                val successful: Boolean
            ) : ParcelableArgs
        }
    }
}

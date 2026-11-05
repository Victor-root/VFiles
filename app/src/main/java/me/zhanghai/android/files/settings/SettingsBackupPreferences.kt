/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.ui.TextFieldDialog
import me.zhanghai.android.files.util.showToast
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import kotlin.system.exitProcess

private const val BACKUP_MIME_TYPE = "application/octet-stream"
private const val BACKUP_FILE_EXTENSION = "vfbackup"

@Composable
fun SettingsBackupPreferences() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var uriToBackUpTo by rememberSaveable { mutableStateOf<Uri?>(null) }
    var uriToRestore by rememberSaveable { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uriToBackUpTo = it }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uriToRestore = it }
    Preference(
        title = stringResource(R.string.settings_backup_create_title),
        icon = painterResource(R.drawable.save_icon_white_24dp),
        summary = stringResource(R.string.settings_backup_create_summary),
        onClick = { backupLauncher.launch("VFiles-${LocalDate.now()}.$BACKUP_FILE_EXTENSION") }
    )
    Preference(
        title = stringResource(R.string.settings_backup_restore_title),
        icon = painterResource(R.drawable.restore_icon_white_24dp),
        summary = stringResource(R.string.settings_backup_restore_summary),
        onClick = {
            if (FileJobService.runningJobCount != 0) {
                context.showToast(R.string.settings_backup_restore_error_jobs)
            } else {
                restoreLauncher.launch(arrayOf("*/*"))
            }
        }
    )
    uriToBackUpTo?.let { uri ->
        PasswordDialog(
            message = stringResource(R.string.settings_backup_create_password_message),
            onConfirm = { password -> coroutineScope.backUp(context, uri, password) },
            onDismissRequest = { uriToBackUpTo = null },
            onCancel = { context.deleteDocument(uri) }
        )
    }
    uriToRestore?.let { uri ->
        PasswordDialog(
            message = stringResource(R.string.settings_backup_restore_password_message),
            onConfirm = { password -> coroutineScope.restore(context, uri, password) },
            onDismissRequest = { uriToRestore = null }
        )
    }
}

@Composable
private fun PasswordDialog(
    message: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit = {}
) {
    val emptyError = stringResource(R.string.settings_backup_password_error_empty)
    TextFieldDialog(
        title = stringResource(R.string.settings_backup_password_title),
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
        validate = { if (it.isEmpty()) emptyError else null },
        keyboardType = KeyboardType.Password,
        trim = false,
        extraContent = {
            Text(
                text = message,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        onCancel = onCancel
    )
}

private fun CoroutineScope.backUp(context: Context, uri: Uri, password: String) {
    launch {
        val success = withContext(Dispatchers.IO) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw FileNotFoundException(uri.toString())
                outputStream.use { SettingsBackup.write(it, password) }
                true
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
        if (success) {
            context.showToast(R.string.settings_backup_create_success)
        } else {
            context.deleteDocument(uri)
            context.showToast(R.string.settings_backup_create_error)
        }
    }
}

private fun CoroutineScope.restore(context: Context, uri: Uri, password: String) {
    launch {
        val errorRes = withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException(uri.toString())
                inputStream.use { SettingsBackup.restore(it, password) }
                null
            } catch (e: SettingsBackupException) {
                e.printStackTrace()
                e.messageRes
            } catch (e: IOException) {
                e.printStackTrace()
                R.string.settings_backup_restore_error_file
            }
        }
        if (errorRes != null) {
            context.showToast(errorRes)
        } else {
            context.restartApp()
        }
    }
}

private fun Context.deleteDocument(uri: Uri) {
    try {
        DocumentsContract.deleteDocument(contentResolver, uri)
    } catch (e: Exception) {
        // The system may hand back something else than a document that can be deleted.
        e.printStackTrace()
    }
}

// Every setting and what's derived from them has to be loaded again, and upgraded if needed.
private fun Context.restartApp() {
    startActivity(
        Intent.makeRestartActivityTask(ComponentName(this, FileListActivity::class.java))
    )
    exitProcess(0)
}

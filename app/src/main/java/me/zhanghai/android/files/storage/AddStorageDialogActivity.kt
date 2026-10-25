/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.file.asExternalStorageUri
import me.zhanghai.android.files.provider.document.resolver.ExternalStorageProviderHacks
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe

class AddStorageDialogActivity : ComposeActivity() {
    @Composable
    override fun Content() {
        AddStorageDialog(onDismissRequest = ::finish)
    }
}

private val STORAGE_TYPES = listOfNotNull(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        R.string.storage_add_storage_android_data to
            AddExternalStorageShortcutActivity::class.createIntent().putArgs(
                AddExternalStorageShortcutActivity.Args(
                    R.string.storage_add_storage_android_data,
                    ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_DATA.asExternalStorageUri()
                )
            )
    } else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        R.string.storage_add_storage_android_obb to
            AddExternalStorageShortcutActivity::class.createIntent().putArgs(
                AddExternalStorageShortcutActivity.Args(
                    R.string.storage_add_storage_android_obb,
                    ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_OBB.asExternalStorageUri()
                )
            )
    } else null,
    R.string.storage_add_storage_document_tree to AddDocumentTreeActivity::class.createIntent(),
    R.string.storage_add_storage_ftp_server to
        EditFtpServerActivity::class.createIntent().putArgs(EditFtpServerActivity.Args()),
    R.string.storage_add_storage_sftp_server to
        EditSftpServerActivity::class.createIntent().putArgs(EditSftpServerActivity.Args()),
    R.string.storage_add_storage_smb_server to AddLanSmbServerActivity::class.createIntent(),
    R.string.storage_add_storage_webdav_server to
        EditWebDavServerActivity::class.createIntent().putArgs(EditWebDavServerActivity.Args())
)

@Composable
private fun AddStorageDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        title = { Text(stringResource(R.string.storage_add_storage_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for ((titleRes, intent) in STORAGE_TYPES) {
                    Text(
                        text = stringResource(titleRes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivitySafe(intent)
                                onDismissRequest()
                            }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    )
}

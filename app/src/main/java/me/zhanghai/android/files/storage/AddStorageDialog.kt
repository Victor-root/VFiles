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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.file.asExternalStorageUri
import me.zhanghai.android.files.provider.document.resolver.ExternalStorageProviderHacks

@Parcelize
data object AddStorageDestination : Destination {
    override val isDialog: Boolean
        get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        AddStorageDialog(onAdd = navigator::replace, onDismissRequest = navigator::goBack)
    }
}

private val STORAGE_TYPES = listOfNotNull(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        R.string.storage_add_storage_android_data to AddExternalStorageShortcutDestination(
            R.string.storage_add_storage_android_data,
            ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_DATA.asExternalStorageUri()
        )
    } else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        R.string.storage_add_storage_android_obb to AddExternalStorageShortcutDestination(
            R.string.storage_add_storage_android_obb,
            ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_OBB.asExternalStorageUri()
        )
    } else null,
    R.string.storage_add_storage_document_tree to AddDocumentTreeDestination,
    R.string.storage_add_storage_ftp_server to EditFtpServerDestination(),
    R.string.storage_add_storage_sftp_server to EditSftpServerDestination(),
    R.string.storage_add_storage_smb_server to AddLanSmbServerDestination,
    R.string.storage_add_storage_webdav_server to EditWebDavServerDestination()
)

@Composable
private fun AddStorageDialog(onAdd: (Destination) -> Unit, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        title = { Text(stringResource(R.string.storage_add_storage_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for ((titleRes, destination) in STORAGE_TYPES) {
                    Text(
                        text = stringResource(titleRes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(destination) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    )
}

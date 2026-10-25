/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.storage.StorageVolumeCustomizations
import me.zhanghai.android.files.storage.VolumeIconType
import me.zhanghai.android.files.ui.TextFieldDialog
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.startActivitySafe

class EditStorageVolumeDialogActivity : ComposeActivity() {
    private val args by args<Args>()

    @Composable
    override fun Content() {
        EditStorageVolumeDialog(args.uuid, args.description, onDismissRequest = { finish() })
    }

    @Parcelize
    class Args(val uuid: String, val description: String) : ParcelableArgs
}

@Composable
private fun EditStorageVolumeDialog(
    uuid: String,
    description: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val customization = remember(uuid) { StorageVolumeCustomizations.get(uuid) }
    var iconType by rememberSaveable {
        mutableStateOf(customization?.iconType ?: VolumeIconType.AUTO)
    }
    TextFieldDialog(
        title = stringResource(R.string.storage_volume_edit_title),
        onConfirm = { StorageVolumeCustomizations.set(uuid, it, iconType) },
        onDismissRequest = onDismissRequest,
        initialText = customization?.name ?: "",
        label = stringResource(R.string.storage_volume_name),
        extraContent = {
            Text(
                text = stringResource(R.string.storage_volume_icon),
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .selectableGroup()
            ) {
                for (type in VolumeIconType.entries) {
                    IconTypeRow(
                        type = type,
                        isSelected = type == iconType,
                        onClick = { iconType = type }
                    )
                }
            }
        },
        placeholder = description,
        neutralButton = {
            TextButton(
                onClick = {
                    // Apps can't unmount volumes themselves (it needs a system-only permission),
                    // so send the user to the system storage settings, where the volume can be
                    // safely ejected.
                    context.startActivitySafe(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                    onDismissRequest()
                }
            ) { Text(stringResource(R.string.storage_volume_eject)) }
        }
    )
}

@Composable
private fun IconTypeRow(type: VolumeIconType, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(
                when (type) {
                    VolumeIconType.AUTO -> R.string.storage_volume_icon_auto
                    VolumeIconType.USB -> R.string.storage_volume_icon_usb
                    VolumeIconType.SD_CARD -> R.string.storage_volume_icon_sd_card
                }
            ),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

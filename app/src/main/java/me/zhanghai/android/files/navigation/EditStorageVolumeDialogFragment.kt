/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.EditStorageVolumeDialogBinding
import me.zhanghai.android.files.storage.StorageVolumeCustomizations
import me.zhanghai.android.files.storage.VolumeIconType
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.setTextWithSelection
import me.zhanghai.android.files.util.startActivitySafe

class EditStorageVolumeDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: EditStorageVolumeDialogBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.storage_volume_edit_title)
            .apply {
                binding = EditStorageVolumeDialogBinding.inflate(context.layoutInflater)
                binding.nameLayout.placeholderText = args.description
                if (savedInstanceState == null) {
                    val customization = StorageVolumeCustomizations.get(args.uuid)
                    binding.nameEdit.setTextWithSelection(customization?.name ?: "")
                    binding.iconGroup.check(
                        when (customization?.iconType ?: VolumeIconType.AUTO) {
                            VolumeIconType.USB -> R.id.iconUsb
                            VolumeIconType.SD_CARD -> R.id.iconSdCard
                            VolumeIconType.AUTO -> R.id.iconAuto
                        }
                    )
                }
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setNeutralButton(R.string.storage_volume_eject) { _, _ -> eject() }
            .create()
            .apply {
                window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }

    private fun save() {
        val customName = binding.nameEdit.text.toString()
        val iconType = when (binding.iconGroup.checkedRadioButtonId) {
            R.id.iconUsb -> VolumeIconType.USB
            R.id.iconSdCard -> VolumeIconType.SD_CARD
            else -> VolumeIconType.AUTO
        }
        StorageVolumeCustomizations.set(args.uuid, customName, iconType)
        finish()
    }

    private fun eject() {
        // Apps can't unmount volumes themselves (it needs a system-only permission), so send the
        // user to the system storage settings, where the volume can be safely ejected.
        startActivitySafe(Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        finish()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    @Parcelize
    class Args(val uuid: String, val description: String) : ParcelableArgs
}

/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.removeFirst
import me.zhanghai.android.files.util.takeIfNotEmpty
import me.zhanghai.android.files.util.valueCompat

/** Which icon to show for a removable volume in the navigation drawer. */
enum class VolumeIconType {
    /** Decide automatically (best-effort detection), falling back to the SD card icon. */
    AUTO,
    USB,
    SD_CARD
}

/**
 * A user customization for an auto-detected removable volume, remembered across re-plugs by its
 * filesystem UUID. Lets the user give the volume a friendly name (since the OS often only exposes
 * the FAT/exFAT serial number, e.g. "FF33-F9E0") and pick a USB / SD card icon.
 */
@Parcelize
data class StorageVolumeCustomization(
    val uuid: String,
    val customName: String?,
    val iconType: VolumeIconType
) : Parcelable {
    val name: String?
        get() = customName?.takeIfNotEmpty()
}

object StorageVolumeCustomizations {
    fun get(uuid: String): StorageVolumeCustomization? =
        Settings.STORAGE_VOLUME_CUSTOMIZATIONS.valueCompat.firstOrNull { it.uuid == uuid }

    fun set(uuid: String, customName: String?, iconType: VolumeIconType) {
        val customizations = Settings.STORAGE_VOLUME_CUSTOMIZATIONS.valueCompat.toMutableList()
            .apply { removeFirst { it.uuid == uuid } }
        val name = customName?.takeIfNotEmpty()
        // Only keep an entry when there's actually something to remember, so cleared customizations
        // don't linger in settings forever.
        if (name != null || iconType != VolumeIconType.AUTO) {
            customizations += StorageVolumeCustomization(uuid, name, iconType)
        }
        Settings.STORAGE_VOLUME_CUSTOMIZATIONS.putValue(customizations)
    }
}

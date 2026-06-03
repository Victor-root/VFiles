/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageVolume
import androidx.annotation.DrawableRes
import androidx.annotation.Size
import androidx.annotation.StringRes
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.about.AboutActivity
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.getDescriptionCompat
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.pathCompat
import me.zhanghai.android.files.compat.uuidCompat
import me.zhanghai.android.files.file.JavaFile
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.ftpserver.FtpServerActivity
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.settings.SettingsActivity
import me.zhanghai.android.files.settings.StandardDirectoryListActivity
import me.zhanghai.android.files.storage.AddStorageDialogActivity
import me.zhanghai.android.files.storage.FileSystemRoot
import me.zhanghai.android.files.storage.Storage
import me.zhanghai.android.files.storage.StorageVolumeCustomization
import me.zhanghai.android.files.storage.StorageVolumeCustomizations
import me.zhanghai.android.files.storage.StorageVolumeListLiveData
import me.zhanghai.android.files.storage.VolumeIconType
import me.zhanghai.android.files.update.UpdateManager
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.isMounted
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.valueCompat

val navigationItems: List<NavigationItem?>
    get() =
        mutableListOf<NavigationItem?>().apply {
            val bookmarkDirectoryItems = bookmarkDirectoryItems
            if (bookmarkDirectoryItems.isNotEmpty()) {
                addAll(bookmarkDirectoryItems)
            } else {
                // No bookmarks yet — show a subtle hint so users discover the feature. It
                // disappears automatically as soon as the first bookmark is added.
                add(BookmarksHintItem())
            }
            val standardDirectoryItems = standardDirectoryItems
            if (standardDirectoryItems.isNotEmpty()) {
                if (isNotEmpty()) {
                    add(null)
                }
                addAll(standardDirectoryItems)
            }
            if (isNotEmpty()) {
                add(null)
            }
            addAll(storageItems)
            if (Environment::class.supportsExternalStorageManager()) {
                // Starting with R, we can get read/write access to non-primary storage volumes with
                // MANAGE_EXTERNAL_STORAGE. However before R, we only have read-only access to them
                // and need to use the Storage Access Framework instead, so hide them in this case
                // to avoid confusion.
                addAll(storageVolumeItems)
            }
            add(AddStorageItem())
            add(null)
            addAll(menuItems)
        }

private val storageItems: List<NavigationItem>
    @Size(min = 0)
    get() =
        Settings.STORAGES.valueCompat.filter { it.isVisible }.map {
            if (it.path != null) PathStorageItem(it) else IntentStorageItem(it)
        }

private abstract class PathItem(val path: Path) : NavigationItem() {
    override fun isChecked(listener: Listener): Boolean = listener.currentPath == path

    override fun onClick(listener: Listener) {
        if (this is NavigationRoot) {
            listener.navigateToRoot(path)
        } else {
            listener.navigateTo(path)
        }
        listener.closeNavigationDrawer()
    }
}

private class PathStorageItem(
    private val storage: Storage
) : PathItem(storage.path!!), NavigationRoot {
    init {
        require(storage.isVisible)
    }

    override val id: Long
        get() = storage.id

    override val iconRes: Int
        @DrawableRes
        get() = storage.iconRes

    override fun getTitle(context: Context): String = storage.getName(context)

    override fun getSubtitle(context: Context): String? =
        storage.linuxPath?.let { getStorageSubtitle(it, context) }

    override fun onLongClick(listener: Listener): Boolean {
        listener.launchIntent(storage.createEditIntent())
        return true
    }

    override fun getName(context: Context): String = getTitle(context)
}

private class IntentStorageItem(
    private val storage: Storage
) : NavigationItem() {
    init {
        require(storage.isVisible)
    }

    override val id: Long
        get() = storage.id

    override val iconRes: Int
        @DrawableRes
        get() = storage.iconRes

    override fun getTitle(context: Context): String = storage.getName(context)

    override fun onClick(listener: Listener) {
        listener.launchIntent(storage.createIntent()!!)
        listener.closeNavigationDrawer()
    }

    override fun onLongClick(listener: Listener): Boolean {
        listener.launchIntent(storage.createEditIntent())
        return true
    }
}

private val storageVolumeItems: List<NavigationItem>
    @Size(min = 0)
    get() {
        val volumes = StorageVolumeListLiveData.valueCompat
            .filter { !it.isPrimaryCompat && it.isMounted }
        if (volumes.isEmpty()) {
            return emptyList()
        }
        val items = mutableListOf<NavigationItem>()
        val usbDeviceCount = usbMassStorageDeviceCount()
        // Group volumes by type; the partitions of one physical drive (a single USB device, or the
        // one SD card slot) get a parent header with their partitions nested (indented) under it.
        for ((type, typeVolumes) in volumes.groupBy { it.resolvedIconType() }) {
            val sorted = typeVolumes.sortedBy { it.uuidCompat.orEmpty() }
            val sameDrive = type != VolumeIconType.USB || usbDeviceCount <= 1
            when {
                sorted.size > 1 && sameDrive -> {
                    // One drive, several partitions → header + indented "Partition N" children.
                    items += StorageVolumeGroupHeaderItem(type)
                    sorted.forEachIndexed { index, volume ->
                        items += StorageVolumeItem(volume, partitionNumber = index + 1)
                    }
                }
                sorted.size > 1 -> {
                    // Several separate same-type drives → number them ("USB drive 1", "USB drive 2").
                    sorted.forEachIndexed { index, volume ->
                        items += StorageVolumeItem(volume, separateNumber = index + 1)
                    }
                }
                else -> items += StorageVolumeItem(sorted.first())
            }
        }
        return items
    }

// A non-interactive parent header for a removable drive whose partitions are listed (indented)
// beneath it.
private class StorageVolumeGroupHeaderItem(
    private val iconType: VolumeIconType
) : NavigationItem() {
    override val id: Long
        get() = ("storageVolumeHeader:" + iconType.name).hashCode().toLong()

    override val iconRes: Int
        @DrawableRes
        get() = when (iconType) {
            VolumeIconType.USB -> R.drawable.usb_icon_white_24dp
            else -> R.drawable.sd_card_icon_white_24dp
        }

    override fun getTitle(context: Context): String =
        context.getString(
            when (iconType) {
                VolumeIconType.USB -> R.string.storage_volume_default_name_usb
                else -> R.string.storage_volume_default_name_sd_card
            }
        )

    override val isHeader: Boolean = true

    override fun onClick(listener: Listener) {}
}

private class StorageVolumeItem(
    private val storageVolume: StorageVolume,
    // >0 when this volume is one partition of a multi-partition drive group: shown as "Partition N"
    // and indented under the group header.
    private val partitionNumber: Int = 0,
    // >0 when this is one of several separate same-type drives: shown as "USB drive N".
    private val separateNumber: Int = 0
) : PathItem(Paths.get(storageVolume.pathCompat)), NavigationRoot {
    private val customization: StorageVolumeCustomization? =
        storageVolume.uuidCompat?.let { StorageVolumeCustomizations.get(it) }

    override val id: Long
        get() = storageVolume.hashCode().toLong()

    override val iconRes: Int
        @DrawableRes
        get() = when (resolveIconType()) {
            VolumeIconType.USB -> R.drawable.usb_icon_white_24dp
            // SD_CARD, and AUTO when detection is inconclusive, both use the SD card icon.
            else -> R.drawable.sd_card_icon_white_24dp
        }

    private fun resolveIconType(): VolumeIconType = storageVolume.resolvedIconType()

    // Partitions of a drive group are indented under their header.
    override val isIndented: Boolean
        get() = partitionNumber > 0

    override fun getTitle(context: Context): String = customization?.name ?: defaultName(context)

    // The name shown when the user hasn't set a custom one: a real OS label if there is one,
    // otherwise a friendly name. Children of a drive group are just "Partition N" (the header
    // already says "USB drive"); separate same-type drives are numbered; a lone drive is unnumbered.
    private fun defaultName(context: Context): String {
        val description = storageVolume.getDescriptionCompat(context)
        if (description.isNotBlank() &&
            !looksLikeRawVolumeId(description, storageVolume.uuidCompat)) {
            return description
        }
        return when {
            partitionNumber > 0 ->
                context.getString(R.string.storage_volume_partition_numbered, partitionNumber)
            separateNumber > 0 -> "${typeName(context)} $separateNumber"
            else -> typeName(context)
        }
    }

    private fun typeName(context: Context): String =
        context.getString(
            when (resolveIconType()) {
                VolumeIconType.USB -> R.string.storage_volume_default_name_usb
                else -> R.string.storage_volume_default_name_sd_card
            }
        )

    override fun getSubtitle(context: Context): String? =
        getStorageSubtitle(storageVolume.pathCompat, context)

    override fun getName(context: Context): String = getTitle(context)

    // Long-press opens a small dialog to rename the volume, choose its icon, or eject it.
    override fun onLongClick(listener: Listener): Boolean {
        val uuid = storageVolume.uuidCompat ?: return false
        listener.launchIntent(
            EditStorageVolumeDialogActivity::class.createIntent()
                .putArgs(EditStorageVolumeDialogFragment.Args(uuid, defaultName(application)))
        )
        return true
    }
}

/**
 * Best-effort guess of whether a removable volume is a USB drive or an SD card. There is no public
 * API for this, so we try the OS description text first, then the (restricted) vold volume id whose
 * major device number distinguishes the bus. Returns null when it can't tell, and the user can
 * always override the icon from the volume's edit dialog.
 */
private fun StorageVolume.detectRemovableType(): VolumeIconType? {
    val description = try {
        getDescriptionCompat(application).lowercase()
    } catch (e: Exception) {
        ""
    }
    when {
        "usb" in description -> return VolumeIconType.USB
        "sd card" in description || "sdcard" in description || "carte sd" in description ->
            return VolumeIconType.SD_CARD
    }
    // The vold volume id (e.g. "public:8,1") encodes the underlying block device major number:
    // 8 = SCSI disk (USB mass storage), 179 = mmc (SD card). Hidden API, so guard heavily.
    try {
        val id = StorageVolume::class.java.getMethod("getId").invoke(this) as? String
        val major = id?.substringAfter(':', "")?.substringBefore(',')?.trim()?.toIntOrNull()
        when (major) {
            8 -> return VolumeIconType.USB
            179 -> return VolumeIconType.SD_CARD
        }
    } catch (e: Throwable) {
        // Hidden-API access blocked or unavailable — fall through.
    }
    // Public-API fallback for when the reflection above is blocked (common on Android 11+): if a
    // USB mass-storage device is plugged into the host port, a removable volume is almost certainly
    // it, since SD cards don't appear on the USB bus.
    if (isUsbMassStorageConnected()) {
        return VolumeIconType.USB
    }
    return null
}

/** Number of USB mass-storage devices currently connected to the USB host port. */
private fun usbMassStorageDeviceCount(): Int =
    try {
        val usbManager = application.getSystemService(Context.USB_SERVICE) as? UsbManager
        usbManager?.deviceList?.values?.count { device ->
            (0 until device.interfaceCount).any {
                device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

private fun isUsbMassStorageConnected(): Boolean = usbMassStorageDeviceCount() > 0

// Resolve the icon type for a volume, honoring any user override and falling back to the SD card
// icon when auto-detection is inconclusive.
private fun StorageVolume.resolvedIconType(): VolumeIconType {
    val custom = uuidCompat?.let { StorageVolumeCustomizations.get(it) }
    return when (val type = custom?.iconType ?: VolumeIconType.AUTO) {
        VolumeIconType.AUTO -> detectRemovableType() ?: VolumeIconType.SD_CARD
        else -> type
    }
}

private val RAW_VOLUME_ID_REGEX = Regex("^\\p{XDigit}{4}-\\p{XDigit}{4}$")

// Whether the OS "description" of a volume is really just its serial / UUID, with no human label
// (e.g. "FF33-F9E0"), so we can show a friendly name instead.
private fun looksLikeRawVolumeId(name: String, uuid: String?): Boolean =
    (uuid != null && name.equals(uuid, ignoreCase = true)) || RAW_VOLUME_ID_REGEX.matches(name)

private fun getStorageSubtitle(linuxPath: String, context: Context): String? {
    var totalSpace = JavaFile.getTotalSpace(linuxPath)
    val freeSpace: Long
    when {
        totalSpace != 0L -> freeSpace = JavaFile.getFreeSpace(linuxPath)
        linuxPath == FileSystemRoot.LINUX_PATH -> {
            // Root directory may not be an actual partition on legacy Android versions (can be
            // a ramdisk instead). On modern Android the system partition will be mounted as
            // root instead so let's try with the system partition again.
            // @see https://source.android.com/devices/bootloader/system-as-root
            val systemPath = Environment.getRootDirectory().path
            totalSpace = JavaFile.getTotalSpace(systemPath)
            freeSpace = JavaFile.getFreeSpace(systemPath)
        }
        else -> freeSpace = 0
    }
    if (totalSpace == 0L) {
        return null
    }
    val freeSpaceString = freeSpace.asFileSize().formatHumanReadable(context)
    val totalSpaceString = totalSpace.asFileSize().formatHumanReadable(context)
    return context.getString(
        R.string.navigation_storage_subtitle_format, freeSpaceString, totalSpaceString
    )
}

private class AddStorageItem : NavigationItem() {
    override val id: Long = R.string.navigation_add_storage.toLong()

    @DrawableRes
    override val iconRes: Int = R.drawable.add_icon_white_24dp

    override fun getTitle(context: Context): String =
        context.getString(R.string.navigation_add_storage)

    override fun onClick(listener: Listener) {
        listener.launchIntent(AddStorageDialogActivity::class.createIntent())
    }
}

private val standardDirectoryItems: List<NavigationItem>
    @Size(min = 0)
    get() =
        StandardDirectoriesLiveData.valueCompat
            .filter { it.isEnabled }
            .map { StandardDirectoryItem(it) }

private class StandardDirectoryItem(
    private val standardDirectory: StandardDirectory
) : PathItem(Paths.get(getExternalStorageDirectory(standardDirectory.relativePath))) {
    init {
        require(standardDirectory.isEnabled)
    }

    override val id: Long
        get() = standardDirectory.id

    override val iconRes: Int
        @DrawableRes
        get() = standardDirectory.iconRes

    override fun getTitle(context: Context): String = standardDirectory.getTitle(context)

    override fun onLongClick(listener: Listener): Boolean {
        listener.launchIntent(StandardDirectoryListActivity::class.createIntent())
        return true
    }
}

val standardDirectories: List<StandardDirectory>
    get() {
        val settingsMap = Settings.STANDARD_DIRECTORY_SETTINGS.valueCompat.associateBy { it.id }
        return defaultStandardDirectories.map {
            val settings = settingsMap[it.key]
            if (settings != null) it.withSettings(settings) else it
        }
    }

private const val relativePathSeparator = ":"

private val defaultStandardDirectories: List<StandardDirectory>
    // HACK: Show QQ, TIM and WeChat standard directories based on whether the directory exists.
    get() =
        DEFAULT_STANDARD_DIRECTORIES.mapNotNull {
            when (it.iconRes) {
                R.drawable.qq_icon_white_24dp, R.drawable.tim_icon_white_24dp,
                R.drawable.wechat_icon_white_24dp -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Direct access to Android/data is blocked since Android 11.
                        null
                    } else {
                        for (relativePath in it.relativePath.split(relativePathSeparator)) {
                            val path = getExternalStorageDirectory(relativePath)
                            if (JavaFile.isDirectory(path)) {
                                return@mapNotNull it.copy(relativePath = relativePath)
                            }
                        }
                        null
                    }
                }
                else -> it
            }
        }

// @see android.os.Environment#STANDARD_DIRECTORIES
private val DEFAULT_STANDARD_DIRECTORIES = listOf(
    StandardDirectory(
        R.drawable.camera_icon_white_24dp, R.string.navigation_standard_directory_dcim,
        Environment.DIRECTORY_DCIM, true
    ),
    StandardDirectory(
        R.drawable.document_icon_white_24dp, R.string.navigation_standard_directory_documents,
        Environment.DIRECTORY_DOCUMENTS, true
    ),
    StandardDirectory(
        R.drawable.image_icon_white_24dp, R.string.navigation_standard_directory_pictures,
        Environment.DIRECTORY_PICTURES, true
    ),
    StandardDirectory(
        R.drawable.audio_icon_white_24dp, R.string.navigation_standard_directory_music,
        Environment.DIRECTORY_MUSIC, true
    ),
    StandardDirectory(
        R.drawable.download_icon_white_24dp, R.string.navigation_standard_directory_downloads,
        Environment.DIRECTORY_DOWNLOADS, true
    ),
    StandardDirectory(
        R.drawable.video_icon_white_24dp, R.string.navigation_standard_directory_movies,
        Environment.DIRECTORY_MOVIES, true
    ),
    StandardDirectory(
        R.drawable.alarm_icon_white_24dp, R.string.navigation_standard_directory_alarms,
        Environment.DIRECTORY_ALARMS, false
    ),
    StandardDirectory(
        R.drawable.notification_icon_white_24dp,
        R.string.navigation_standard_directory_notifications, Environment.DIRECTORY_NOTIFICATIONS,
        false
    ),
    StandardDirectory(
        R.drawable.podcast_icon_white_24dp, R.string.navigation_standard_directory_podcasts,
        Environment.DIRECTORY_PODCASTS, false
    ),
    StandardDirectory(
        R.drawable.ringtone_icon_white_24dp, R.string.navigation_standard_directory_ringtones,
        Environment.DIRECTORY_RINGTONES, false
    ),
    StandardDirectory(
        R.drawable.qq_icon_white_24dp, R.string.navigation_standard_directory_qq,
        listOf("Android/data/com.tencent.mobileqq/Tencent/QQfile_recv", "Tencent/QQfile_recv")
            .joinToString(relativePathSeparator), true
    ),
    StandardDirectory(
        R.drawable.tim_icon_white_24dp, R.string.navigation_standard_directory_tim,
        listOf("Android/data/com.tencent.tim/Tencent/TIMfile_recv", "Tencent/TIMfile_recv")
            .joinToString(relativePathSeparator), true
    ),
    StandardDirectory(
        R.drawable.wechat_icon_white_24dp, R.string.navigation_standard_directory_wechat,
        listOf("Android/data/com.tencent.mm/MicroMsg/Download", "Tencent/MicroMsg/Download")
            .joinToString(relativePathSeparator), true
    )
)

internal fun getExternalStorageDirectory(relativePath: String): String =
    @Suppress("DEPRECATION")
    Environment.getExternalStoragePublicDirectory(relativePath).path

private class BookmarksHintItem : NavigationItem() {
    override val id: Long = R.string.navigation_bookmarks_hint_title.toLong()

    @DrawableRes
    override val iconRes: Int = R.drawable.bookmark_outline_icon_white_24dp

    override fun getTitle(context: Context): String =
        context.getString(R.string.navigation_bookmarks_hint_title)

    override fun getSubtitle(context: Context): String =
        context.getString(R.string.navigation_bookmarks_hint_subtitle)

    override fun onClick(listener: Listener) {
        listener.showToast(R.string.navigation_bookmarks_hint_toast)
    }
}

private val bookmarkDirectoryItems: List<NavigationItem>
    @Size(min = 0)
    get() = Settings.BOOKMARK_DIRECTORIES.valueCompat.map { BookmarkDirectoryItem(it) }

private class BookmarkDirectoryItem(
    private val bookmarkDirectory: BookmarkDirectory
) : PathItem(bookmarkDirectory.path) {
    // We cannot simply use super.getId() because different bookmark directories may have
    // the same path.
    override val id: Long
        get() = bookmarkDirectory.id

    @DrawableRes
    override val iconRes: Int = R.drawable.bookmark_icon_white_24dp

    override fun getTitle(context: Context): String = bookmarkDirectory.name

    override fun onLongClick(listener: Listener): Boolean {
        listener.launchIntent(
            EditBookmarkDirectoryDialogActivity::class.createIntent()
                .putArgs(EditBookmarkDirectoryDialogFragment.Args(bookmarkDirectory))
        )
        return true
    }
}

private val menuItems: List<NavigationItem>
    @Size(3)
    get() = listOf(
        IntentMenuItem(
            R.drawable.ftp_server_icon_white_24dp, R.string.navigation_ftp_server,
            FtpServerActivity::class.createIntent()
        ),
        IntentMenuItem(
            R.drawable.settings_icon_white_24dp, R.string.navigation_settings,
            SettingsActivity::class.createIntent()
        ),
        IntentMenuItem(
            R.drawable.about_icon_white_24dp, R.string.navigation_about,
            AboutActivity::class.createIntent(),
            showBadge = UpdateManager.isUpdateAvailable()
        )
    )

private abstract class MenuItem(
    @DrawableRes override val iconRes: Int,
    @StringRes val titleRes: Int,
    override val showBadge: Boolean = false
) : NavigationItem() {
    override fun getTitle(context: Context): String = context.getString(titleRes)
}

private class IntentMenuItem(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    private val intent: Intent,
    showBadge: Boolean = false
) : MenuItem(iconRes, titleRes, showBadge) {
    override val id: Long
        get() = intent.component.hashCode().toLong()

    override fun onClick(listener: Listener) {
        listener.launchIntent(intent)
        listener.closeNavigationDrawer()
    }
}

/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.linux

import android.app.AppOpsManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import me.zhanghai.android.files.app.appOpsManager
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.AppOpsManagerCompat
import me.zhanghai.android.files.compat.checkOpRawNoThrowCompat
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.opPackageNameCompat
import me.zhanghai.android.files.compat.pathFileCompat
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.root.RootablePath
import me.zhanghai.android.files.storage.StorageVolumeList
import me.zhanghai.android.files.util.readParcelable
import java.io.File
import java.io.IOException

internal class LinuxPath : ByteStringListPath<LinuxPath>, RootablePath {
    private val fileSystem: LinuxFileSystem

    constructor(fileSystem: LinuxFileSystem, path: ByteString) : super(
        LinuxFileSystem.SEPARATOR, path
    ) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: LinuxFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(LinuxFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == LinuxFileSystem.SEPARATOR

    override fun createPath(path: ByteString): LinuxPath = LinuxPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): LinuxPath =
        LinuxPath(fileSystem, absolute, segments)

    override val defaultDirectory: LinuxPath
        get() = fileSystem.defaultDirectory

    override fun getFileSystem(): LinuxFileSystem = fileSystem

    override fun getRoot(): LinuxPath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): LinuxPath {
        throw UnsupportedOperationException()
    }

    override fun toFile(): File = File(toString())

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey {
        if (watcher !is LocalLinuxWatchService) {
            throw ProviderMismatchException(watcher.toString())
        }
        return watcher.register(this, events, *modifiers)
    }

    override fun isRootRequired(isAttributeAccess: Boolean): Boolean {
        val file = toFile()
        return StorageVolumeList.value.none {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !it.isPrimaryCompat) {
                return@none false
            }
            val storageVolumeDirectory = it.pathFileCompat
            if (!file.startsWith(storageVolumeDirectory)) {
                return@none false
            }
            return@none file.isAccessibleInStorageVolume(storageVolumeDirectory, isAttributeAccess)
        }
    }

    private fun File.isAccessibleInStorageVolume(
        storageVolumeDirectory: File,
        isAttributeAccess: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val parentDirectory = parentFile
            val androidDataDirectory = storageVolumeDirectory.resolve(FILE_ANDROID_DATA)
            val isInAndroidDataDirectory = if (isAttributeAccess && parentDirectory != null) {
                parentDirectory.startsWith(androidDataDirectory)
            } else {
                startsWith(androidDataDirectory)
            }
            val appPackageName = application.packageName
            if (isInAndroidDataDirectory) {
                val appDataDirectory = androidDataDirectory.resolve(appPackageName)
                return startsWith(appDataDirectory)
            }
            val androidObbDirectory = storageVolumeDirectory.resolve(FILE_ANDROID_OBB)
            val isInAndroidObbDirectory = if (isAttributeAccess && parentDirectory != null) {
                parentDirectory.startsWith(androidObbDirectory)
            } else {
                startsWith(androidObbDirectory)
            }
            if (isInAndroidObbDirectory) {
                // REQUEST_INSTALL_PACKAGES gives access to Android/obb. Since S,
                // StorageManagerService doesn't restart us when it's given for the access to apply
                // at once, which isn't handled, like for the access to all files.
                if (isRequestInstallPackagesAllowed()) {
                    return true
                }
                val appObbDirectory = androidObbDirectory.resolve(appPackageName)
                return startsWith(appObbDirectory)
            }
        }
        return true
    }

    private constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        private val FILE_ANDROID_DATA = File("Android/data")
        private val FILE_ANDROID_OBB = File("Android/obb")

        @JvmField
        val CREATOR = object : Parcelable.Creator<LinuxPath> {
            override fun createFromParcel(source: Parcel): LinuxPath = LinuxPath(source)

            override fun newArray(size: Int): Array<LinuxPath?> = arrayOfNulls(size)
        }

        // IPC for checking the app op is expensive, and we'll be killed by StorageManagerService
        // when losing the app op, so the result is cached once it was allowed.
        @Volatile
        private var wasRequestInstallPackagesAllowed = false

        private fun isRequestInstallPackagesAllowed(): Boolean {
            if (wasRequestInstallPackagesAllowed) {
                return true
            }
            // We'll never have the signature|appop permission itself, so only the app op is
            // checked against MODE_ALLOWED.
            return (
                appOpsManager.checkOpRawNoThrowCompat(
                    AppOpsManagerCompat.OPSTR_REQUEST_INSTALL_PACKAGES,
                    Process.myUid(),
                    application.opPackageNameCompat,
                    null
                ) == AppOpsManager.MODE_ALLOWED
            )
                .also { wasRequestInstallPackagesAllowed = it }
        }
    }
}

val Path.isLinuxPath: Boolean
    get() = this is LinuxPath

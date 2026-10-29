/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.remote

import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.provider.FileSystemProviders
import java.io.IOException
import java.io.InterruptedIOException

open class RemoteFileServiceInterface : IRemoteFileService.Stub() {
    override fun getRemoteFileSystemProviderInterface(scheme: String): IRemoteFileSystemProvider =
        RemoteFileSystemProviderInterface(FileSystemProviders[scheme])

    override fun getRemoteFileSystemInterface(fileSystem: ParcelableObject): IRemoteFileSystem =
        RemoteFileSystemInterface(fileSystem.value())

    override fun getRemotePosixFileStoreInterface(
        fileStore: ParcelableObject
    ): IRemotePosixFileStore = RemotePosixFileStoreInterface(fileStore.value())

    override fun getRemotePosixFileAttributeViewInterface(
        attributeView: ParcelableObject
    ): IRemotePosixFileAttributeView =
        RemotePosixFileAttributeViewInterface(attributeView.value())

    // The same command as the one adb can run, as root or the shell UID of Shizuku.
    override fun allowExternalStorageManager(exception: ParcelableException) {
        tryRun(exception) {
            val process = ProcessBuilder(
                "/system/bin/cmd", "appops", "set", "--uid", BuildConfig.APPLICATION_ID,
                "MANAGE_EXTERNAL_STORAGE", "allow"
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitValue = try {
                process.waitFor()
            } catch (e: InterruptedException) {
                throw InterruptedIOException().apply { initCause(e) }
            }
            if (exitValue != 0) {
                throw IOException(output.trim())
            }
        }
    }
}

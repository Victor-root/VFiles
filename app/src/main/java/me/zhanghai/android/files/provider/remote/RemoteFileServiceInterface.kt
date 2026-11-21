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

    // The same commands as the ones adb can run, as root or the shell UID of Shizuku.
    override fun allowExternalStorageManager(exception: ParcelableException) {
        tryRun(exception) {
            runCommand(
                "/system/bin/cmd", "appops", "set", "--uid", BuildConfig.APPLICATION_ID,
                "MANAGE_EXTERNAL_STORAGE", "allow"
            )
        }
    }

    // Only the disabling that can be undone, which keeps the package and its data. Undoing it also
    // brings the package back if it was removed for the user, and puts back its original state.
    override fun setPackageEnabled(
        packageName: String,
        userId: Int,
        isEnabled: Boolean,
        exception: ParcelableException
    ) {
        tryRun(exception) {
            val user = userId.toString()
            if (isEnabled) {
                runCommand("/system/bin/pm", "install-existing", "--user", user, packageName)
                runCommand("/system/bin/pm", "default-state", "--user", user, packageName)
            } else {
                runCommand("/system/bin/pm", "disable-user", "--user", user, packageName)
            }
        }
    }

    @Throws(IOException::class)
    private fun runCommand(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
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

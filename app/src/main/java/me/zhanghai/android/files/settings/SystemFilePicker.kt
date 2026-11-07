/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.provider.root.RootFileService
import me.zhanghai.android.files.util.hasBits
import java.io.IOException

/**
 * The file picker of Android, which VFiles takes the place of once disabled. It's only ever
 * disabled, like with `pm disable-user`, so that enabling it again brings everything back. It may
 * also have been removed for the user with adb, which Android keeps it around for.
 */
object SystemFilePicker {
    private const val DOCUMENTS_UI_NAME = "documentsui"

    // Android TV has no file picker, but this stub that does nothing takes the same requests.
    private const val TV_STUBS_PACKAGE_NAME = "com.android.tv.frameworkpackagestubs"

    /**
     * Found by name rather than by what they handle, since a maker may have other apps of its own
     * handling the same requests.
     */
    private val applications: List<ApplicationInfo>
        get() =
            packageManager
                .getInstalledApplications(
                    PackageManager.MATCH_DISABLED_COMPONENTS or
                        PackageManager.MATCH_UNINSTALLED_PACKAGES
                )
                .filter {
                    it.flags.hasBits(ApplicationInfo.FLAG_SYSTEM) &&
                        (DOCUMENTS_UI_NAME in it.packageName ||
                            it.packageName == TV_STUBS_PACKAGE_NAME)
                }
                .sortedBy { it.packageName }

    val isAvailable: Boolean
        get() = applications.isNotEmpty()

    val isDisabled: Boolean
        get() = applications.let { it.isNotEmpty() && it.all { app -> app.isDisabledOrRemoved } }

    private val ApplicationInfo.isRemoved: Boolean
        get() = !flags.hasBits(ApplicationInfo.FLAG_INSTALLED)

    private val ApplicationInfo.isDisabledOrRemoved: Boolean
        get() =
            isRemoved ||
                when (packageManager.getApplicationEnabledSetting(packageName)) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> true
                    else -> false
                }

    /** What restores the file picker from a computer, for when the app can't do it anymore. */
    val enableCommand: String
        get() =
            applications.joinToString("\n") {
                val command = if (it.isRemoved) "install-existing" else "enable"
                createCommand(command, it.packageName)
            }

    val disableCommand: String
        get() =
            applications.joinToString("\n") { createCommand("disable-user", it.packageName) }

    private fun createCommand(command: String, packageName: String): String =
        "adb shell pm $command --user $userId $packageName"

    private val userId: Int
        get() = Process.myUserHandle().hashCode()

    @Throws(IOException::class)
    fun setDisabled(isDisabled: Boolean) {
        for (application in applications) {
            RootFileService.setPackageEnabled(application.packageName, userId, !isDisabled)
        }
        if (this.isDisabled != isDisabled) {
            throw IOException("The file picker didn't change, it's disabled: ${this.isDisabled}")
        }
    }
}

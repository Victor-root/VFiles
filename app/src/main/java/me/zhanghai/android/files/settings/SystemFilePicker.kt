/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.provider.root.RootFileService
import java.io.IOException

/**
 * The file picker of Android, which VFiles takes the place of once disabled. It's only ever
 * disabled, like with `pm disable-user`, so that enabling it again brings everything back.
 */
object SystemFilePicker {
    private const val DOCUMENTS_UI_NAME = "documentsui"

    // Android TV has no file picker, but this stub that does nothing takes the same requests.
    private const val TV_STUBS_PACKAGE_NAME = "com.android.tv.frameworkpackagestubs"

    /**
     * The packages of the system answering requests for files, including disabled ones. Only the
     * known ones count, since a maker may have other apps of its own among them.
     */
    val packageNames: List<String>
        get() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
            return packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_SYSTEM_ONLY
            )
                .map { it.activityInfo.packageName }
                .filter { DOCUMENTS_UI_NAME in it || it == TV_STUBS_PACKAGE_NAME }
                .distinct()
        }

    val isDisabled: Boolean
        get() = packageNames.let { names -> names.isNotEmpty() && names.all { it.isDisabled } }

    private val String.isDisabled: Boolean
        get() =
            when (packageManager.getApplicationEnabledSetting(this)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> true
                else -> false
            }

    /** What restores the file picker from a computer, for when the app can't do it anymore. */
    val enableCommand: String
        get() = createCommand("enable")

    val disableCommand: String
        get() = createCommand("disable-user")

    private fun createCommand(command: String): String =
        packageNames.joinToString("\n") { "adb shell pm $command --user $userId $it" }

    private val userId: Int
        get() = Process.myUserHandle().hashCode()

    @Throws(IOException::class)
    fun setDisabled(isDisabled: Boolean) {
        for (packageName in packageNames) {
            RootFileService.setPackageEnabled(packageName, userId, !isDisabled)
        }
        if (this.isDisabled != isDisabled) {
            throw IOException("The file picker didn't change, it's disabled: ${this.isDisabled}")
        }
    }
}

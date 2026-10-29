/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.packageManager
import kotlin.reflect.KClass

// TvSettings didn't have "All files access" page until Android 13. The access may also have been
// given without the page, like with adb.
@ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
fun KClass<Environment>.supportsExternalStorageManager(): Boolean =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> true
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager() ||
                isManageAppAllFilesAccessPermissionIntentResolved
        else -> false
    }

/**
 * Whether the access to all files can only be given with adb, on devices without a page for it,
 * like TVs whose maker replaced it, while the app only has the access to media without it.
 */
fun KClass<Environment>.needsAdbForExternalStorageManager(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !supportsExternalStorageManager()

// Whether the app currently holds full access to external storage, through whichever mechanism this
// device offers. Deliberately keyed on supportsExternalStorageManager() rather than the API level:
// a device without an "All files access" page can never grant that special access, so it has to be
// asked for the legacy permission instead.
fun KClass<Environment>.isExternalStorageAccessGranted(): Boolean =
    if (supportsExternalStorageManager()) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            application, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

@RequiresApi(Build.VERSION_CODES.R)
fun KClass<Environment>.createManageAppAllFilesAccessPermissionIntent(packageName: String): Intent =
    createAllFilesAccessIntent(packageName).setComponent(televisionAllFilesAccessActivity)

private fun createAllFilesAccessIntent(packageName: String): Intent =
    Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", packageName, null)
    )

// On TVs, the page is the one of the settings app, which other apps of the maker may take over
// without offering the access, like TCL's Permission Shield, so it's opened directly.
@delegate:RequiresApi(Build.VERSION_CODES.R)
private val televisionAllFilesAccessActivity: ComponentName? by lazy(LazyThreadSafetyMode.NONE) {
    if (!application.isTelevision) {
        return@lazy null
    }
    packageManager.findSettingsActivity(createAllFilesAccessIntent(application.packageName))
        .also {
            if (BuildConfig.DEBUG) {
                Log.d("EnvironmentExtensions", "All files access page on TV: $it")
            }
        }
}

@delegate:RequiresApi(Build.VERSION_CODES.R)
private val isManageAppAllFilesAccessPermissionIntentResolved: Boolean
    by lazy(LazyThreadSafetyMode.NONE) {
        if (application.isTelevision) {
            televisionAllFilesAccessActivity != null
        } else {
            createAllFilesAccessIntent(application.packageName).resolveActivity(packageManager) !=
                null
        }
    }

/** Returns the activity of the settings app among those handling [intent], if any. */
internal fun PackageManager.findSettingsActivity(intent: Intent): ComponentName? {
    val settingsPackageNames = queryIntentActivities(Intent(Settings.ACTION_SETTINGS), 0)
        .mapTo(mutableSetOf()) { it.activityInfo.packageName }
    return queryIntentActivities(intent, 0)
        .map { it.activityInfo }
        // Only an exported activity can be opened directly.
        .firstOrNull { it.exported && it.packageName in settingsPackageNames }
        ?.let { ComponentName(it.packageName, it.name) }
}

/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.packageManager
import kotlin.reflect.KClass

// TvSettings didn't have "All files access" page until Android 13.
@ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
fun KClass<Environment>.supportsExternalStorageManager(): Boolean =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> true
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            isManageAppAllFilesAccessPermissionIntentResolved
        else -> false
    }

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
    Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", packageName, null)
    )

@delegate:RequiresApi(Build.VERSION_CODES.R)
private val isManageAppAllFilesAccessPermissionIntentResolved: Boolean
    by lazy(LazyThreadSafetyMode.NONE) {
        Environment::class.createManageAppAllFilesAccessPermissionIntent(application.packageName)
            .resolveActivity(packageManager) != null
    }

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EnvironmentExtensionsTest {
    private val packageManager =
        ApplicationProvider.getApplicationContext<Context>().packageManager
    private val allFilesAccessIntent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", "fr.vroot.vfiles", null)
    )

    @Before
    fun addSettingsApp() {
        addActivity(Intent(Settings.ACTION_SETTINGS), TV_SETTINGS, "MainSettings")
    }

    @Test
    fun findsSettingsPageWhenAnotherAppTakesItOver() {
        // Like TCL's Permission Shield, which doesn't offer the access.
        addActivity(allFilesAccessIntent, TCL_GUARD, "PermissionShieldActivity", priority = 100)
        addActivity(allFilesAccessIntent, TV_SETTINGS, "AppManageExternalStorageActivity")
        assertEquals(
            ComponentName(TV_SETTINGS, "AppManageExternalStorageActivity"),
            packageManager.findSettingsActivity(allFilesAccessIntent)
        )
    }

    @Test
    fun findsNothingWithoutSettingsPage() {
        addActivity(allFilesAccessIntent, TCL_GUARD, "PermissionShieldActivity", priority = 100)
        assertNull(packageManager.findSettingsActivity(allFilesAccessIntent))
    }

    private fun addActivity(intent: Intent, packageName: String, name: String, priority: Int = 0) {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                this.name = name
                exported = true
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
            this.priority = priority
        }
        shadowOf(packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    companion object {
        private const val TV_SETTINGS = "com.android.tv.settings"
        private const val TCL_GUARD = "com.tcl.guard"
    }
}

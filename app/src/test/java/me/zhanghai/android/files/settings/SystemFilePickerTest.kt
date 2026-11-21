/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.test.AppProviderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemFilePickerTest {
    @get:Rule
    val appProviderRule = AppProviderRule()

    private fun addSystemFileApp(packageName: String) {
        shadowOf(packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_INSTALLED
                }
            }
        )
    }

    private fun setEnabledSetting(packageName: String, setting: Int) {
        packageManager.setApplicationEnabledSetting(packageName, setting, 0)
    }

    @Test
    fun ignoresOtherFileAppsOfTheSystem() {
        addSystemFileApp("com.maker.files")
        assertFalse(SystemFilePicker.isAvailable)
    }

    @Test
    fun findsPickerOnceDisabled() {
        addSystemFileApp(DOCUMENTS_UI)
        setEnabledSetting(DOCUMENTS_UI, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
        assertTrue(SystemFilePicker.isAvailable)
    }

    @Test
    fun includesStubOfAndroidTv() {
        addSystemFileApp(TV_STUBS)
        assertTrue(SystemFilePicker.isAvailable)
    }

    @Test
    fun isDisabledOnlyOnceDisabledByUser() {
        addSystemFileApp(DOCUMENTS_UI)
        assertFalse(SystemFilePicker.isDisabled)
        setEnabledSetting(DOCUMENTS_UI, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
        assertTrue(SystemFilePicker.isDisabled)
        setEnabledSetting(DOCUMENTS_UI, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        assertFalse(SystemFilePicker.isDisabled)
    }

    @Test
    fun isNotDisabledWithoutAnyPicker() {
        assertFalse(SystemFilePicker.isDisabled)
    }

    @Test
    fun givesCommandsThatCanBeUndone() {
        addSystemFileApp(DOCUMENTS_UI)
        assertEquals(
            "adb shell pm enable --user 0 $DOCUMENTS_UI", SystemFilePicker.enableCommand
        )
        assertEquals(
            "adb shell pm disable-user --user 0 $DOCUMENTS_UI", SystemFilePicker.disableCommand
        )
    }

    private companion object {
        const val DOCUMENTS_UI = "com.android.documentsui"
        const val TV_STUBS = "com.android.tv.frameworkpackagestubs"
    }
}

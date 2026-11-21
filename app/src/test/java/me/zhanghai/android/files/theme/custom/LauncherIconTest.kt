/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {
    @get:Rule
    val appProviderRule = AppProviderRule()

    @Test
    fun everyColorHasAnAliasButNotTheDynamicOne() {
        assertEquals(20, LauncherIcon.colors.size)
        assertFalse(ThemeColor.DYNAMIC in LauncherIcon.colors)
        for (color in LauncherIcon.colors) {
            // Throws if the manifest doesn't declare it.
            application.packageManager.getActivityInfo(
                color.launcherAlias, PackageManager.MATCH_DISABLED_COMPONENTS
            )
        }
    }

    @Test
    fun startsWithDefaultIcon() {
        assertEquals(ThemeColor.COLOR_PRIMARY, LauncherIcon.currentColor)
    }

    @Test
    fun nearestColorOfThemeColorIsItself() {
        for (color in LauncherIcon.colors) {
            val argb = ContextCompat.getColor(application, color.resourceId)
            val nearest = LauncherIcon.nearestColor(argb)
            assertEquals(
                ContextCompat.getColor(application, nearest.resourceId), argb
            )
        }
    }

    @Test
    fun enablesOnlyAppliedColor() {
        runBlocking { LauncherIcon.apply(ThemeColor.MATERIAL_TEAL) }
        assertEquals(ThemeColor.MATERIAL_TEAL, LauncherIcon.currentColor)
        assertEquals(ThemeColor.MATERIAL_TEAL, Settings.LAUNCHER_ICON_COLOR.storedValue)
        val enabledColors = LauncherIcon.colors.filter {
            application.packageManager.getComponentEnabledSetting(it.launcherAlias) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        assertEquals(listOf(ThemeColor.MATERIAL_TEAL), enabledColors)
    }

    @Test
    fun appliesColorOfRestoredBackup() {
        Settings.LAUNCHER_ICON_COLOR.putValue(ThemeColor.MATERIAL_GREEN)
        LauncherIcon.applySavedColor()
        assertEquals(ThemeColor.MATERIAL_GREEN, LauncherIcon.currentColor)
    }
}

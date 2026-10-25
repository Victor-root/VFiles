/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import me.zhanghai.android.files.theme.custom.ThemeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppColorSchemeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun lightSchemeHasLightBackground() {
        val colorScheme = appColorScheme(context, ThemeColor.COLOR_PRIMARY, false, false)
        assertTrue(colorScheme.background.luminance() > 0.5f)
        assertTrue(colorScheme.onBackground.luminance() < 0.5f)
    }

    @Test
    fun themeColorIsUsedAsPrimary() {
        // google_blue_600 and google_blue_300.
        assertEquals(
            Color(0xFF1A73E8),
            appColorScheme(context, ThemeColor.COLOR_PRIMARY, false, false).primary
        )
        assertEquals(
            Color(0xFF8AB4F8),
            appColorScheme(context, ThemeColor.COLOR_PRIMARY, true, false).primary
        )
    }

    @Test
    fun darkSchemeHasDarkBackground() {
        val colorScheme = appColorScheme(context, ThemeColor.COLOR_PRIMARY, true, false)
        assertTrue(colorScheme.background.luminance() < 0.5f)
        assertNotEquals(Color.Black, colorScheme.background)
    }

    @Test
    fun blackNightModeOnlyAppliesInDarkTheme() {
        val dark = appColorScheme(context, ThemeColor.MATERIAL_TEAL, true, true)
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.Black, dark.surface)
        assertNotEquals(Color.Black, dark.surfaceContainerHigh)
        val light = appColorScheme(context, ThemeColor.MATERIAL_TEAL, false, true)
        assertNotEquals(Color.Black, light.background)
    }

    @Test
    fun themeColorsProduceDistinctPrimaries() {
        val primaries = ThemeColor.entries.filter { it != ThemeColor.DYNAMIC }.map {
            appColorScheme(context, it, false, false).primary
        }
        // Grey and blue grey are close but still distinct.
        assertEquals(primaries.size, primaries.toSet().size)
    }

    @Test
    fun dynamicColorFallsBackWithoutWallpaper() {
        val colorScheme = appColorScheme(context, ThemeColor.DYNAMIC, false, false)
        assertTrue(colorScheme.background.luminance() > 0.5f)
    }
}

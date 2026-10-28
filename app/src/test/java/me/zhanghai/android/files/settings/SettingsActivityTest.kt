/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.text.TextUtils
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.leaveTouchMode
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsActivityTest {
    private val composeTestRule = createAndroidComposeRule<SettingsActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.FOLLOW_SYSTEM)
        Settings.BLACK_NIGHT_MODE.putValue(false)
        Settings.THEME_COLOR.putValue(ThemeColor.COLOR_PRIMARY)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun focusesFirstSettingWithDpad() {
        // Like when opening the settings with a D-pad.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Language").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun movesFocusFromAppBarToSettingsWithDpad() {
        composeTestRule.activityRule.scenario.onActivity { it.leaveTouchMode() }
        composeTestRule.onNodeWithContentDescription("Navigate up").requestFocus()
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.onNodeWithText("Language").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun opensStoragesAndComesBackWithDpad() {
        composeTestRule.activityRule.scenario.onActivity { it.leaveTouchMode() }
        val storages = composeTestRule.onNodeWithText("Storage").performScrollTo()
        storages.requestFocus()
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        // Opened above the settings, in the same activity.
        composeTestRule.onNodeWithText("Interface").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Add storage").assertIsDisplayed()
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        storages.assertIsFocused()
        // Up and down go on from there.
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        storages.assertIsNotFocused()
    }

    @Test
    fun showsSettings() {
        composeTestRule.onNodeWithText("Interface").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme color").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/settings.png")
        composeTestRule.onNodeWithText("Behavior").performScrollTo().assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/settings_behavior.png")
    }

    @Test
    fun switchUpdatesSetting() {
        val node = composeTestRule.onNodeWithText("Black night mode")
        node.assertIsOff()
        node.performClick()
        node.assertIsOn()
        assertEquals(true, Settings.BLACK_NIGHT_MODE.value)
    }

    @Test
    fun nightModeMenuUpdatesSetting() {
        composeTestRule.onNodeWithText("Night mode").performClick()
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/settings_night_mode_menu.png")
        composeTestRule.onNodeWithText("On").performClick()
        composeTestRule.waitForIdle()
        assertEquals(NightMode.ON, Settings.NIGHT_MODE.value)
        composeTestRule.onNodeWithText("On").assertIsDisplayed()
    }

    @Test
    fun fileNameEllipsizeMenuUpdatesSetting() {
        composeTestRule.onNodeWithText("Display long file name").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Marquee").performClick()
        composeTestRule.waitForIdle()
        assertEquals(TextUtils.TruncateAt.MARQUEE, Settings.FILE_NAME_ELLIPSIZE.value)
    }

    @Test
    fun themeColorDialogUpdatesSetting() {
        composeTestRule.onNodeWithText("Theme color").performClick()
        composeTestRule.onNodeWithText("Other colors").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/settings_theme_color.png")
        composeTestRule.onNodeWithText("Default").performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        // The default is red, even where the wallpaper color is available.
        assertEquals(ThemeColor.MATERIAL_RED, Settings.THEME_COLOR.value)
    }
}

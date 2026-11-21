/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.about

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.setDestinationContent
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Test
    fun showsAppInfoAndOpensLicenses() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        composeTestRule.setDestinationContent(AboutDestination)
        composeTestRule.onNodeWithText("VFiles").assertIsDisplayed()
        composeTestRule.onNodeWithText("Version").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/about_light.png")
        composeTestRule.onNodeWithText("Licenses").performClick()
        composeTestRule.onNodeWithText("Copyright 2018 Hai Zhang", substring = true)
            .assertExists()
        captureScreenRoboImage("build/screenshots/about_licenses.png")
    }

    @Test
    fun followsNightModeSetting() {
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.setDestinationContent(AboutDestination)
        captureScreenRoboImage("build/screenshots/about_dark.png")
    }
}

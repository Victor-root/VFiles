/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.onboarding

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.Process
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.core.content.getSystemService
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowEnvironment

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingActivityTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Test
    fun asksForPermissions() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        launchWithoutFilesAccess()
        composeTestRule.onNodeWithText("Required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Granted").assertIsDisplayed()
        composeTestRule.onNodeWithText("MANAGE_EXTERNAL_STORAGE allow", substring = true)
            .assertDoesNotExist()
        // Files access is required to continue.
        composeTestRule.onNodeWithText("Get started").assertIsNotEnabled()
        captureScreenRoboImage("build/screenshots/onboarding.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/onboarding_dark.png")
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun fitsOnTelevision() {
        launchWithoutFilesAccess()
        // Every permission fits side by side, without scrolling.
        val grantButtons = composeTestRule.onAllNodesWithText("Grant").assertCountEquals(3)
        repeat(3) { grantButtons[it].assertIsDisplayed() }
        composeTestRule.onNodeWithText("Get started").assertIsDisplayed()
        // Reaching the last button with the D-pad doesn't scroll the title away.
        repeat(3) { composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Welcome to VFiles").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/onboarding_television.png")
    }

    private fun launchWithoutFilesAccess() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        // The access to all files isn't granted.
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowEnvironment.addExternalDir("external")
        shadowOf(context.getSystemService<AppOpsManager>()).setMode(
            "android:manage_external_storage", Process.myUid(), context.packageName,
            AppOpsManager.MODE_IGNORED
        )
        ActivityScenario.launch(OnboardingActivity::class.java)
        composeTestRule.waitForIdle()
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        composeTestRule.onNodeWithText("Required").assertIsDisplayed()
        // Files access is required to continue.
        composeTestRule.onNodeWithText("Get started").assertIsNotEnabled()
        captureScreenRoboImage("build/screenshots/onboarding.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/onboarding_dark.png")
    }
}

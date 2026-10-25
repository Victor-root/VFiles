/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.AppTheme
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The server states that the service can't be put in from a test. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FtpServerScreenTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.FTP_SERVER_ANONYMOUS_LOGIN.putValue(false)
    }

    @Test
    fun configurationIsDisabledWhileRunning() {
        composeTestRule.setContent { AppTheme { FtpServerScreen(FtpServerService.State.RUNNING) } }
        composeTestRule.onNodeWithText("Status").assertIsOn()
        composeTestRule.onNodeWithText("Running").assertIsDisplayed()
        composeTestRule.onNodeWithText("URL").assertIsEnabled()
        for (title in listOf(
            "Anonymous login", "Username", "Password", "Port", "Root folder", "Allow writing"
        )) {
            composeTestRule.onNodeWithText(title).assertIsNotEnabled()
        }
        captureScreenRoboImage("build/screenshots/ftp_server_running.png")
    }

    @Test
    fun configurationIsEnabledWhileStopping() {
        composeTestRule.setContent {
            AppTheme { FtpServerScreen(FtpServerService.State.STOPPING) }
        }
        composeTestRule.onNodeWithText("Stopping…").assertIsDisplayed()
        // Not disabled while the state changes, so that it keeps the D-pad focus on a TV.
        composeTestRule.onNodeWithText("Status").assertIsEnabled()
        composeTestRule.onNodeWithText("Port").assertIsEnabled()
    }
}

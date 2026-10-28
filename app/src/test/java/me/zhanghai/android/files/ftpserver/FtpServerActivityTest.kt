/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java8.nio.file.Paths
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.leaveTouchMode
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.extraPath
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FtpServerActivityTest {
    private val composeTestRule = createAndroidComposeRule<FtpServerActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.FTP_SERVER_ANONYMOUS_LOGIN.putValue(true)
        Settings.FTP_SERVER_USERNAME.putValue("admin")
        Settings.FTP_SERVER_PASSWORD.putValue("")
        Settings.FTP_SERVER_PORT.putValue(2121)
        Settings.FTP_SERVER_HOME_DIRECTORY.putValue(Paths.get("/storage/emulated/0"))
        Settings.FTP_SERVER_WRITABLE.putValue(true)
    }

    @Test
    fun showsServer() {
        composeTestRule.onNodeWithText("FTP server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status").assertIsOff()
        composeTestRule.onNodeWithText("Not started").assertIsDisplayed()
        composeTestRule.onNodeWithText("Configuration").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not set").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/ftp_server_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/ftp_server_dark.png")
    }

    @Test
    fun anonymousLoginDisablesUsernameAndPassword() {
        composeTestRule.onNodeWithText("Username").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Password").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Port").assertIsEnabled()
        composeTestRule.onNodeWithText("Anonymous login").performClick()
        assertEquals(false, Settings.FTP_SERVER_ANONYMOUS_LOGIN.value)
        composeTestRule.onNodeWithText("Username").assertIsEnabled()
        composeTestRule.onNodeWithText("Password").assertIsEnabled()
    }

    @Test
    fun statusStartsServer() {
        composeTestRule.onNodeWithText("Status").performClick()
        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .nextStartedService
        assertEquals(FtpServerService::class.java.name, intent.component!!.className)
    }

    @Test
    fun emptyUsernameFallsBackToDefault() {
        Settings.FTP_SERVER_ANONYMOUS_LOGIN.putValue(false)
        editText("Username", "alice")
        assertEquals("alice", Settings.FTP_SERVER_USERNAME.value)
        composeTestRule.onNodeWithText("alice").assertIsDisplayed()
        editText("Username", "")
        assertEquals("admin", Settings.FTP_SERVER_USERNAME.value)
    }

    @Test
    fun passwordIsMaskedAndKeptAsTyped() {
        Settings.FTP_SERVER_ANONYMOUS_LOGIN.putValue(false)
        editText("Password", " secret")
        assertEquals(" secret", Settings.FTP_SERVER_PASSWORD.value)
        composeTestRule.onNodeWithText("•".repeat(7)).assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/ftp_server_password.png")
    }

    @Test
    fun portIsValidated() {
        composeTestRule.onNodeWithText("Port").performClick()
        val textField = composeTestRule.onNode(hasSetTextAction())
        textField.performTextReplacement("-1")
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithText("Invalid port").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/ftp_server_port_invalid.png")
        textField.performTextReplacement("2222")
        composeTestRule.onNodeWithText("OK").performClick()
        assertEquals(2222, Settings.FTP_SERVER_PORT.value)
        composeTestRule.onNodeWithText("2222").assertIsDisplayed()
    }

    @Test
    fun homeDirectoryIsPicked() {
        composeTestRule.onNodeWithText("Root folder").performClick()
        val shadowActivity = shadowOf(composeTestRule.activity)
        val intent = shadowActivity.nextStartedActivityForResult.intent
        assertEquals(FileListActivity::class.java.name, intent.component!!.className)
        assertEquals(Paths.get("/storage/emulated/0"), intent.extraPath)
        val path = Paths.get("/storage/emulated/0/Music")
        shadowActivity.receiveResult(
            intent, Activity.RESULT_OK, Intent().apply { extraPath = path }
        )
        composeTestRule.waitForIdle()
        assertEquals(path, Settings.FTP_SERVER_HOME_DIRECTORY.value)
    }

    @Test
    fun urlMenuCopiesPassword() {
        assumeTrue(FtpServerUrl.getUrl() != null)
        Settings.FTP_SERVER_ANONYMOUS_LOGIN.putValue(false)
        Settings.FTP_SERVER_PASSWORD.putValue("secret")
        composeTestRule.onNodeWithText("URL").performClick()
        composeTestRule.onNodeWithText("Copy URL").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/ftp_server_url_menu.png")
        composeTestRule.onNodeWithText("Copy password").performClick()
        assertEquals("secret", clipboardManager.primaryClip!!.getItemAt(0).text)
    }

    @Test
    fun dpadMovesFocusAcrossPreferences() {
        composeTestRule.runOnUiThread { composeTestRule.activity.leaveTouchMode() }
        composeTestRule.onNodeWithText("Status")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        val root = composeTestRule.onRoot()
        root.performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.onNodeWithText("URL").assertIsFocused()
        root.performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.onNodeWithText("Add to Quick Settings").assertIsFocused()
        root.performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.onNodeWithText("Anonymous login").assertIsFocused()
        root.performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(false, Settings.FTP_SERVER_ANONYMOUS_LOGIN.value)
    }

    private fun editText(title: String, text: String) {
        composeTestRule.onNodeWithText(title).performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement(text)
        composeTestRule.onNodeWithText("OK").performClick()
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.hierynomus.smbj.auth.AuthenticationContext
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.provider.ftp.client.Mode
import me.zhanghai.android.files.provider.ftp.client.Protocol
import me.zhanghai.android.files.provider.sftp.client.PasswordAuthentication
import me.zhanghai.android.files.provider.webdav.client.AccessTokenAuthentication
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.DestinationTestActivity
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import me.zhanghai.android.files.provider.ftp.client.Authority as FtpAuthority
import me.zhanghai.android.files.provider.sftp.client.Authority as SftpAuthority
import me.zhanghai.android.files.provider.smb.client.Authority as SmbAuthority
import me.zhanghai.android.files.provider.webdav.client.Authority as WebDavAuthority
import me.zhanghai.android.files.provider.webdav.client.Protocol as WebDavProtocol

@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditServerScreensTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.STORAGES.putValue(emptyList())
    }

    @Test
    fun ftpServerScreenShowsErrors() {
        val scenario = launch(EditFtpServerDestination())
        composeTestRule.onNodeWithText("Add FTP server").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_edit_ftp_server_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/storage_edit_ftp_server_dark.png")
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        textField("Port").performTextReplacement("port")
        composeTestRule.onNodeWithText("Connect and add").performClick()
        composeTestRule.onNodeWithText("Enter a hostname").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invalid port").assertIsDisplayed()
        textField("Hostname").assertIsFocused()
        composeTestRule.onNodeWithText("Enter a username").performScrollTo().assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_edit_ftp_server_errors.png")
        textField("Hostname").performTextReplacement("bad host")
        composeTestRule.onNodeWithText("Enter a hostname").assertDoesNotExist()
        composeTestRule.onNodeWithText("Add").performClick()
        composeTestRule.onNodeWithText("Invalid hostname").assertIsDisplayed()
        assertTrue(Settings.STORAGES.value.isEmpty())
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }

    @Test
    fun ftpServerScreenKeepsStateOnRecreation() {
        val scenario = launch(EditFtpServerDestination())
        textField("Hostname").performTextReplacement("example.com")
        textField("Port").performTextReplacement("port")
        dropdown("Authentication").performClick()
        composeTestRule.onNodeWithText("Anonymous").performClick()
        composeTestRule.onNodeWithText("Connect and add").performClick()
        composeTestRule.onNodeWithText("Invalid port").assertIsDisplayed()
        scenario.recreate()
        textField("Hostname").assert(hasText("example.com"))
        composeTestRule.onNodeWithText("Invalid port").assertIsDisplayed()
        dropdown("Authentication").assert(hasText("Anonymous"))
        composeTestRule.onNodeWithText("Username").assertDoesNotExist()
    }

    @Test
    fun ftpServerScreenAddsServer() {
        val scenario = launch(EditFtpServerDestination(host = "192.168.1.2"))
        textField("Hostname").assert(hasText("192.168.1.2"))
        dropdown("Protocol").performClick()
        composeTestRule.onNodeWithText("FTPS").performClick()
        // The default port follows the protocol.
        textField("Port").assertIsDisplayed().assert(hasText("990"))
        textField("Username").performTextReplacement("user")
        textField("Password").performTextReplacement("secret")
        textField("Name").assert(hasText("user@192.168.1.2"))
        composeTestRule.onNodeWithText("Add").performClick()
        // Closed once added or saved, without a result now that it's a screen of the app.
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val server = Settings.STORAGES.value.single() as FtpServer
        assertEquals(
            FtpAuthority(Protocol.FTPS, "192.168.1.2", 990, "user", Mode.PASSIVE, "UTF-8"),
            server.authority
        )
        assertEquals("secret", server.password)
        assertEquals(null, server.customName)
        assertEquals("", server.relativePath)
    }

    @Test
    fun ftpServerScreenEditsAnonymousServer() {
        val server = FtpServer(
            1L, "My FTP",
            FtpAuthority(
                Protocol.FTP, "example.com", 2121, FtpAuthority.ANONYMOUS_USERNAME, Mode.ACTIVE,
                "UTF-8"
            ),
            FtpAuthority.ANONYMOUS_PASSWORD, "pub"
        )
        Settings.STORAGES.putValue(listOf(server))
        val scenario = launch(EditFtpServerDestination(server))
        composeTestRule.onNodeWithText("Edit FTP server").assertIsDisplayed()
        textField("2121").assertIsDisplayed()
        textField("pub").assertIsDisplayed()
        textField("My FTP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Anonymous").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertDoesNotExist()
        captureScreenRoboImage("build/screenshots/storage_edit_ftp_server_edit.png")
        textField("My FTP").performTextReplacement("")
        composeTestRule.onNodeWithText("Save").performClick()
        // Closed once added or saved, without a result now that it's a screen of the app.
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val savedServer = Settings.STORAGES.value.single() as FtpServer
        assertEquals(server.id, savedServer.id)
        assertEquals(null, savedServer.customName)
        assertEquals(server.authority, savedServer.authority)
        assertEquals(server.password, savedServer.password)
        assertEquals("pub", savedServer.relativePath)
    }

    @Test
    fun ftpServerScreenRemovesServer() {
        val server = FtpServer(
            1L, null,
            FtpAuthority(Protocol.FTP, "example.com", 21, "user", Mode.PASSIVE, "UTF-8"),
            "secret", ""
        )
        Settings.STORAGES.putValue(listOf(server))
        val scenario = launch(EditFtpServerDestination(server))
        textField("user").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").performClick()
        // Closed once added or saved, without a result now that it's a screen of the app.
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertTrue(Settings.STORAGES.value.isEmpty())
    }

    @Test
    fun dropdownOpensWithDpadCenter() {
        launch(EditFtpServerDestination())
        val protocolNode = dropdown("Protocol")
        protocolNode.performSemanticsAction(SemanticsActions.RequestFocus)
        protocolNode.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.onNodeWithText("FTPES").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_edit_ftp_server_protocol_menu.png")
    }

    @Test
    fun sftpServerScreenValidatesPrivateKey() {
        launch(EditSftpServerDestination())
        textField("Hostname").performTextReplacement("example.com")
        textField("Username").performTextReplacement("user")
        dropdown("Authentication").performClick()
        composeTestRule.onNodeWithText("Public key").performClick()
        composeTestRule.onNodeWithText("Connect and add").performClick()
        composeTestRule.onNodeWithText("Enter a private key").performScrollTo().assertIsDisplayed()
        textField("Private key").performTextReplacement("not a key")
        composeTestRule.onNodeWithText("Enter a private key").assertDoesNotExist()
        composeTestRule.onNodeWithText("Connect and add").performClick()
        composeTestRule.onNodeWithText("Invalid private key").performScrollTo()
            .assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_edit_sftp_server_errors.png")
        // Editing the private key password also clears the private key error.
        textField("Private key password").performTextReplacement("password")
        composeTestRule.onNodeWithText("Invalid private key").assertDoesNotExist()
        assertTrue(Settings.STORAGES.value.isEmpty())
    }

    @Test
    fun sftpServerScreenSavesServer() {
        val server = SftpServer(
            1L, null, SftpAuthority("example.com", 2222, "user"), PasswordAuthentication("pw"),
            "home"
        )
        Settings.STORAGES.putValue(listOf(server))
        val scenario = launch(EditSftpServerDestination(server))
        composeTestRule.onNodeWithText("Edit SFTP server").assertIsDisplayed()
        textField("Name").assert(hasText("user@example.com:2222/home"))
        captureScreenRoboImage("build/screenshots/storage_edit_sftp_server_edit.png")
        textField("2222").performTextReplacement("")
        composeTestRule.onNodeWithText("Save").performClick()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val savedServer = Settings.STORAGES.value.single() as SftpServer
        assertEquals(SftpAuthority("example.com", 22, "user"), savedServer.authority)
        assertEquals(PasswordAuthentication("pw"), savedServer.authentication)
    }

    @Test
    fun smbServerScreenEditsGuestServer() {
        val guest = AuthenticationContext.guest()
        val server = SmbServer(
            1L, null, SmbAuthority("nas", 445, guest.username, guest.domain, false),
            guest.password.concatToString(), "share"
        )
        Settings.STORAGES.putValue(listOf(server))
        val scenario = launch(EditSmbServerDestination(server))
        composeTestRule.onNodeWithText("Guest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Domain").assertDoesNotExist()
        val encryptNode = composeTestRule.onNodeWithText("SMB encryption").performScrollTo()
        captureScreenRoboImage("build/screenshots/storage_edit_smb_server_edit.png")
        encryptNode.performClick()
        composeTestRule.onNodeWithText("Save").performClick()
        // Closed once added or saved, without a result now that it's a screen of the app.
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val savedServer = Settings.STORAGES.value.single() as SmbServer
        assertEquals(
            SmbAuthority("nas", 445, guest.username, guest.domain, true), savedServer.authority
        )
        assertEquals(server.password, savedServer.password)
    }

    @Test
    fun smbServerAddedFromLanServerListClosesIt() {
        val scenario = launch(StorageListDestination)
        composeTestRule.onNodeWithContentDescription("Add storage").performClick()
        composeTestRule.onNodeWithText("SMB server").performClick()
        composeTestRule.onNodeWithText("Add manually").performClick()
        composeTestRule.waitForIdle()
        textField("Hostname").performTextReplacement("nas")
        textField("Username").performTextReplacement("user")
        textField("Password").performTextReplacement("secret")
        composeTestRule.onNodeWithText("Add").performClick()
        composeTestRule.waitForIdle()
        // Back to the storages, without the list of the servers on the LAN in between.
        composeTestRule.onNodeWithText("user@nas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add manually").assertDoesNotExist()
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
        assertEquals("nas", (Settings.STORAGES.value.single() as SmbServer).authority.host)
    }

    @Test
    fun smbServerScreenAddsPasswordServer() {
        val scenario = launch(EditSmbServerDestination(host = "nas"))
        composeTestRule.onNodeWithText("Add SMB server").assertIsDisplayed()
        composeTestRule.onNodeWithText("SMB encryption").performScrollTo().assertIsOff()
        textField("Username").performTextReplacement("user")
        textField("Domain").performTextReplacement("WORKGROUP")
        textField("Password").performTextReplacement("secret")
        captureScreenRoboImage("build/screenshots/storage_edit_smb_server_add.png")
        composeTestRule.onNodeWithText("Add").performClick()
        // Closed once added or saved, without a result now that it's a screen of the app.
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val savedServer = Settings.STORAGES.value.single() as SmbServer
        assertEquals(
            SmbAuthority("nas", 445, "user", "WORKGROUP", false), savedServer.authority
        )
        assertEquals("secret", savedServer.password)
    }

    @Test
    fun webDavServerScreenRequiresAccessToken() {
        val scenario = launch(EditWebDavServerDestination(host = "dav.example.com"))
        textField("Port").assert(hasText("443"))
        dropdown("Authentication").performClick()
        composeTestRule.onNodeWithText("Access token").performClick()
        composeTestRule.onNodeWithText("Add").performClick()
        composeTestRule.onNodeWithText("Enter an access token").performScrollTo()
            .assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_edit_webdav_server_errors.png")
        composeTestRule.onNode(
            hasSetTextAction() and hasText("Access token") and !hasText("Authentication")
        ).performTextReplacement("token")
        composeTestRule.onNodeWithText("Add").performClick()
        // Closed once added or saved, without a result now that it's a screen of the app.
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val savedServer = Settings.STORAGES.value.single() as WebDavServer
        assertEquals(
            WebDavAuthority(WebDavProtocol.DAVS, "dav.example.com", 443, ""),
            savedServer.authority
        )
        assertEquals(AccessTokenAuthentication("token"), savedServer.authentication)
    }

    private fun textField(text: String): SemanticsNodeInteraction =
        composeTestRule.onNode(hasSetTextAction() and hasText(text))

    private fun dropdown(label: String): SemanticsNodeInteraction =
        composeTestRule.onNode(hasText(label) and hasClickAction())

    private fun launch(destination: Destination): ActivityScenario<DestinationTestActivity> =
        DestinationTestActivity.launch(destination)

    @Test
    fun focusedFieldOpensKeyboard() {
        launch(EditFtpServerDestination())
        textField("Hostname").performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        assertTrue(isKeyboardShown())
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun focusedFieldOpensKeyboardOnlyOnceClickedOnTelevision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        launch(EditFtpServerDestination())
        val hostname = textField("Hostname")
        hostname.performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        hostname.assertIsFocused()
        assertFalse(isKeyboardShown())
        hostname.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        assertTrue(isKeyboardShown())
        // Moving on to the next field closes it, until clicked again.
        textField("Port").performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        assertFalse(isKeyboardShown())
        textField("Port").performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        assertTrue(isKeyboardShown())
    }

    private fun isKeyboardShown(): Boolean =
        shadowOf(
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(InputMethodManager::class.java)
        ).isSoftInputVisible
}


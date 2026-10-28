/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.activity.ComponentActivity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.provider.ftp.client.Authority
import me.zhanghai.android.files.provider.ftp.client.Mode
import me.zhanghai.android.files.provider.ftp.client.Protocol
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.AppTheme
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalComposeUiApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageListScreenTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private val fileSystemRoot = FileSystemRoot(null, false)
    private val primaryStorageVolume = PrimaryStorageVolume(null, true)
    private val ftpServer = FtpServer(
        1L, "Server", Authority(Protocol.FTP, "example.com", 21, "user", Mode.PASSIVE, "UTF-8"),
        "secret", ""
    )

    private lateinit var inputModeManager: InputModeManager

    @Before
    fun setUp() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.STORAGES.putValue(listOf(fileSystemRoot, primaryStorageVolume, ftpServer))
        composeTestRule.setContent {
            inputModeManager = LocalInputModeManager.current
            AppTheme { StorageListScreen() }
        }
    }

    @Test
    fun showsStorages() {
        composeTestRule.onNodeWithText("Root").assertIsDisplayed()
        composeTestRule.onNodeWithText("/storage/emulated/0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_list_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/storage_list_dark.png")
    }

    @Test
    fun showsEmptyList() {
        Settings.STORAGES.putValue(emptyList())
        composeTestRule.onNodeWithText("No storage").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_list_empty.png")
    }

    @Test
    fun opensEditorAndAddStorage() {
        composeTestRule.onNodeWithText("Internal storage").performClick()
        val activity = shadowOf(composeTestRule.activity)
        assertEquals(
            EditDeviceStorageDialogActivity::class.java.name,
            activity.nextStartedActivity.component!!.className
        )
        composeTestRule.onNodeWithContentDescription("Add storage").performClick()
        assertEquals(
            AddStorageDialogActivity::class.java.name,
            activity.nextStartedActivity.component!!.className
        )
    }

    @Test
    fun dragHandleReordersStorages() {
        composeTestRule.onNodeWithText("Root").performTouchInput {
            val dragHandle = Offset(width - 36.dp.toPx(), center.y)
            down(dragHandle)
            // Over the middle of the next item, then of the one after.
            repeat(20) { moveBy(Offset(0f, height * 2 / 20f)) }
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(
            listOf(primaryStorageVolume.id, ftpServer.id, fileSystemRoot.id),
            Settings.STORAGES.value.map { it.id }
        )
    }

    @Test
    fun itemsAreReachableWithDpad() {
        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        val rootNode = composeTestRule.onNodeWithText("Root")
        rootNode.performSemanticsAction(SemanticsActions.RequestFocus)
        rootNode.performKeyInput { pressKey(Key.DirectionDown) }
        val primaryStorageVolumeNode = composeTestRule.onNodeWithText("Internal storage")
        primaryStorageVolumeNode.assertIsFocused()
        primaryStorageVolumeNode.performKeyInput { pressKey(Key.DirectionDown) }
        val ftpServerNode = composeTestRule.onNodeWithText("Server")
        ftpServerNode.assertIsFocused()
        ftpServerNode.performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(
            EditFtpServerActivity::class.java.name,
            shadowOf(composeTestRule.activity).nextStartedActivity.component!!.className
        )
    }
}

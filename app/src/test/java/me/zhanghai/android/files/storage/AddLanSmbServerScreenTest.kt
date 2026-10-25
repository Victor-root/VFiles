/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.AppTheme
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.net.InetAddress

@OptIn(ExperimentalComposeUiApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddLanSmbServerScreenTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private val server = LanSmbServer("NAS", InetAddress.getByName("192.168.1.2"))

    private var lanSmbServerList by mutableStateOf<Stateful<List<LanSmbServer>>>(Loading(null))
    private val addedServers = mutableListOf<LanSmbServer?>()
    private var refreshCount = 0
    private lateinit var inputModeManager: InputModeManager

    @Before
    fun setUp() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        composeTestRule.setContent {
            inputModeManager = LocalInputModeManager.current
            AppTheme {
                AddLanSmbServerScreen(
                    lanSmbServerList = lanSmbServerList,
                    onRefresh = { ++refreshCount },
                    onAddServer = { addedServers += it }
                )
            }
        }
    }

    @Test
    fun showsSearchThenServers() {
        composeTestRule.onNodeWithText("Searching for SMB servers…").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_add_lan_smb_server_loading.png")
        lanSmbServerList = Success(listOf(server))
        composeTestRule.onNodeWithText("Searching for SMB servers…").assertDoesNotExist()
        composeTestRule.onNodeWithText("192.168.1.2").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_add_lan_smb_server_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/storage_add_lan_smb_server_dark.png")
    }

    @Test
    fun keepsServersWhileReloading() {
        lanSmbServerList = Loading(listOf(server))
        composeTestRule.onNodeWithText("NAS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Searching for SMB servers…").assertDoesNotExist()
    }

    @Test
    fun addsFoundOrManualServer() {
        lanSmbServerList = Success(listOf(server))
        composeTestRule.onNodeWithText("NAS").performClick()
        composeTestRule.onNodeWithText("Add manually").performClick()
        assertEquals(listOf(server, null), addedServers)
    }

    @Test
    fun itemsAreReachableWithDpad() {
        lanSmbServerList = Success(listOf(server))
        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        val serverNode = composeTestRule.onNodeWithText("NAS")
        serverNode.performSemanticsAction(SemanticsActions.RequestFocus)
        serverNode.assertIsFocused()
        serverNode.performKeyInput { pressKey(Key.DirectionDown) }
        val addNode = composeTestRule.onNodeWithText("Add manually")
        addNode.assertIsFocused()
        addNode.performKeyInput { pressKey(Key.DirectionUp) }
        serverNode.assertIsFocused()
        serverNode.performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(server), addedServers)
    }

    @Test
    fun pullsToRefreshWhenNotLoading() {
        swipeDownList()
        assertEquals(0, refreshCount)
        lanSmbServerList = Success(emptyList())
        swipeDownList()
        assertEquals(1, refreshCount)
    }

    private fun swipeDownList() {
        composeTestRule.onRoot().performTouchInput {
            swipeDown(startY = height * 0.3f, endY = height * 0.9f)
        }
        composeTestRule.waitForIdle()
    }
}

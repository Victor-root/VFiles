/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Environment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.navigation.StandardDirectory
import me.zhanghai.android.files.navigation.StandardDirectorySettings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.leaveTouchMode
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.valueCompat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StandardDirectoryListActivityTest {
    private val composeTestRule = createAndroidComposeRule<StandardDirectoryListActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.STANDARD_DIRECTORY_SETTINGS.putValue(emptyList())
    }

    private fun scrollTo(text: String): SemanticsNodeInteraction {
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        return composeTestRule.onNodeWithText(text)
    }

    @Test
    fun showsStandardDirectories() {
        composeTestRule.onNodeWithText("Standard folders").assertIsDisplayed()
        composeTestRule.onNodeWithText("DCIM").assertIsOn()
        scrollTo("Alarms").assertIsOff()
        captureScreenRoboImage("build/screenshots/standard_directory_list_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/standard_directory_list_dark.png")
    }

    @Test
    fun showsGalleryFirst() {
        composeTestRule.onNodeWithText("Gallery").assertIsOn()
        composeTestRule.onNodeWithText("Photos and videos of the device").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gallery").performClick()
        assertEquals(
            listOf(StandardDirectorySettings(StandardDirectory.GALLERY_KEY, null, false)),
            Settings.STANDARD_DIRECTORY_SETTINGS.valueCompat
        )
    }

    @Test
    fun clickTogglesStandardDirectory() {
        val node = scrollTo("Alarms")
        node.performClick()
        node.assertIsOn()
        assertEquals(
            listOf(StandardDirectorySettings(Environment.DIRECTORY_ALARMS, null, true)),
            Settings.STANDARD_DIRECTORY_SETTINGS.valueCompat
        )
        node.performClick()
        node.assertIsOff()
        assertEquals(
            listOf(StandardDirectorySettings(Environment.DIRECTORY_ALARMS, null, false)),
            Settings.STANDARD_DIRECTORY_SETTINGS.valueCompat
        )
    }

    @Test
    fun dpadMovesFocusAcrossStandardDirectories() {
        composeTestRule.runOnUiThread { composeTestRule.activity.leaveTouchMode() }
        val dcimNode = composeTestRule.onNodeWithText("DCIM")
        dcimNode.performSemanticsAction(SemanticsActions.RequestFocus)
        val root = composeTestRule.onRoot()
        root.performKeyInput { pressKey(Key.DirectionDown) }
        val documentsNode = composeTestRule.onNodeWithText("Documents")
        documentsNode.assertIsFocused()
        root.performKeyInput { pressKey(Key.DirectionCenter) }
        documentsNode.assertIsOff()
        root.performKeyInput { pressKey(Key.DirectionUp) }
        dcimNode.assertIsFocused()
    }

}

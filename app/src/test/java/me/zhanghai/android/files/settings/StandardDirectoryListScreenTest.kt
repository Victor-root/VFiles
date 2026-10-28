/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.navigation.StandardDirectories
import me.zhanghai.android.files.navigation.StandardDirectory
import me.zhanghai.android.files.navigation.StandardDirectorySettings
import me.zhanghai.android.files.navigation.standardDirectories
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.leaveTouchMode
import me.zhanghai.android.files.test.setDestinationContent
import me.zhanghai.android.files.theme.night.NightMode
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
class StandardDirectoryListScreenTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Before
    fun setUp() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.STANDARD_DIRECTORY_SETTINGS.putValue(emptyList())
        Settings.STANDARD_DIRECTORY_ORDER.putValue(emptyList())
        composeTestRule.setDestinationContent(StandardDirectoryListDestination)
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
            Settings.STANDARD_DIRECTORY_SETTINGS.value
        )
    }

    @Test
    fun clickTogglesStandardDirectory() {
        val node = scrollTo("Alarms")
        node.performClick()
        node.assertIsOn()
        assertEquals(
            listOf(StandardDirectorySettings(Environment.DIRECTORY_ALARMS, null, true)),
            Settings.STANDARD_DIRECTORY_SETTINGS.value
        )
        node.performClick()
        node.assertIsOff()
        assertEquals(
            listOf(StandardDirectorySettings(Environment.DIRECTORY_ALARMS, null, false)),
            Settings.STANDARD_DIRECTORY_SETTINGS.value
        )
    }

    @Test
    fun dragHandleMovesFirstStandardDirectory() {
        val rowHeight = composeTestRule.onNodeWithText("Gallery").fetchSemanticsNode().size.height
        composeTestRule.onAllNodesWithContentDescription("Move")[0].performTouchInput {
            down(center)
            // Past the middle of the next row, but not of the one after.
            val distance = rowHeight * 1.2f
            repeat(10) { moveBy(Offset(0f, distance / 10)) }
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(
            listOf(Environment.DIRECTORY_DCIM, StandardDirectory.GALLERY_KEY),
            StandardDirectories.value.take(2).map { it.key }
        )
        // The list stays at its top rather than following the first item.
        composeTestRule.onNodeWithText("DCIM").assertIsDisplayed()
    }

    @Test
    fun dpadReordersStandardDirectories() {
        composeTestRule.runOnUiThread { composeTestRule.activity.leaveTouchMode() }
        composeTestRule.onAllNodesWithContentDescription("Move")[1]
            .performSemanticsAction(SemanticsActions.RequestFocus)
        val root = composeTestRule.onRoot()
        // The center key picks DCIM up, down moves it, and the center key puts it down.
        root.performKeyInput { pressKey(Key.DirectionCenter) }
        root.performKeyInput { pressKey(Key.DirectionDown) }
        root.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        assertEquals(
            listOf(
                StandardDirectory.GALLERY_KEY, Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_DCIM
            ),
            StandardDirectories.value.take(3).map { it.key }
        )
        captureScreenRoboImage("build/screenshots/standard_directory_list_reordered.png")
    }

    @Test
    fun keepsNewStandardDirectoryInPlaceWhenReordered() {
        // DCIM moved below Documents, before Pictures was added.
        Settings.STANDARD_DIRECTORY_ORDER.putValue(
            listOf(
                StandardDirectory.GALLERY_KEY, Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MUSIC
            )
        )
        assertEquals(
            listOf(
                StandardDirectory.GALLERY_KEY, Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_MUSIC
            ),
            standardDirectories.take(5).map { it.key }
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

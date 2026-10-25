/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Intent
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.TestFileContentProvider
import me.zhanghai.android.files.test.waitForBackgroundWork
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextEditorActivityTest {
    private val temporaryFolder = TemporaryFolder()

    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain.outerRule(AppProviderRule()).around(temporaryFolder).around(composeTestRule)

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
    }

    private fun launch(text: String): ActivityScenario<TextEditorActivity> {
        TestFileContentProvider.install()
        val file = temporaryFolder.newFile("notes.txt").apply { writeText(text) }
        val uri = TestFileContentProvider.addFile(file, "text/plain")
        val intent = Intent(
            Intent.ACTION_VIEW, uri, ApplicationProvider.getApplicationContext(),
            TextEditorActivity::class.java
        )
        return ActivityScenario.launch<TextEditorActivity>(intent).also {
            // Wait for the file to be read.
            composeTestRule.waitForBackgroundWork {
                composeTestRule
                    .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                    .fetchSemanticsNodes().isEmpty()
            }
        }
    }

    @Test
    fun showsFileContent() {
        launch("First line\nSecond line")
        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("First line\nSecond line").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/text_editor.png")
    }

    @Test
    fun editingMarksTheTitleAndBackAsksToDiscard() {
        val scenario = launch("Text")
        composeTestRule.onNodeWithText("Text").performTextInput("More ")
        composeTestRule.onNodeWithText("*notes.txt").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()
        composeTestRule.onNodeWithText("Keep editing").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/text_editor_discard.png")
        composeTestRule.onNodeWithText("Keep editing").performClick()
        composeTestRule.onNodeWithText("*notes.txt").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()
        composeTestRule.onNodeWithText("Discard").performClick()
        scenario.onActivity { assertTrue(it.isFinishing) }
    }

    @Test
    fun reloadDiscardsChangesAfterConfirmation() {
        launch("Text")
        composeTestRule.onNodeWithText("Text").performTextInput("More ")
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Reload").performClick()
        composeTestRule.onNodeWithText("Reload").performClick()
        // Reloading reads the file in the background.
        composeTestRule.waitForBackgroundWork {
            composeTestRule.onAllNodesWithText("Text").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
    }

    @Test
    fun encodingDialogShowsCharsets() {
        launch("Text")
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Encoding").performClick()
        composeTestRule.onNodeWithText("UTF-8").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/text_editor_encoding.png")
    }

    @Test
    fun showsErrorForTooLargeFile() {
        launch("a".repeat(1024 * 1024 + 1))
        composeTestRule.onNodeWithText("too large", substring = true).assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/text_editor_error.png")
    }
}

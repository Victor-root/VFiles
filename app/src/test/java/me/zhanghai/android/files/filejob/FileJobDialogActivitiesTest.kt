/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.TestDocumentsProvider
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileJobDialogActivitiesTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private class ConflictResult(
        val action: FileJobConflictAction,
        val name: String?,
        val isAll: Boolean
    )

    private fun launchConflictDialog(onResult: (ConflictResult) -> Unit) {
        val rootPath = TestDocumentsProvider.install()
        TestDocumentsProvider.addDirectory("Downloads")
        TestDocumentsProvider.addFile("Downloads/notes.txt", "text/plain", 1024)
        TestDocumentsProvider.addFile("notes.txt", "text/plain", 2048)
        val sourceFile = rootPath.resolve("Downloads/notes.txt").loadFileItem()
        val targetFile = rootPath.resolve("notes.txt").loadFileItem()
        val args = FileJobConflictDialogActivity.Args(
            sourceFile, targetFile, CopyMoveType.COPY
        ) { action, name, isAll -> onResult(ConflictResult(action, name, isAll)) }
        ActivityScenario.launch<FileJobConflictDialogActivity>(
            FileJobConflictDialogActivity::class.createIntent().putArgs(args)
        )
        composeTestRule.waitForIdle()
    }

    @Test
    fun replacesForAllFiles() {
        var result: ConflictResult? = null
        launchConflictDialog { result = it }
        composeTestRule.onNodeWithText("Replace file “notes.txt”?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Original file").assertIsDisplayed()
        composeTestRule.onNodeWithText("Replace with").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_job_conflict.png")
        composeTestRule.onNodeWithText("Apply this action to all files").performClick()
        composeTestRule.onNodeWithText("Replace").performClick()
        composeTestRule.waitForIdle()
        val conflictResult = result!!
        assertEquals(FileJobConflictAction.MERGE_OR_REPLACE, conflictResult.action)
        assertEquals(true, conflictResult.isAll)
    }

    @Test
    fun renamesOnlyThisFile() {
        var result: ConflictResult? = null
        launchConflictDialog { result = it }
        composeTestRule.onNodeWithText("Apply this action to all files").performClick()
        composeTestRule.onNodeWithText("Select a new name for the destination").performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("notes 2.txt")
        composeTestRule.onNodeWithText("Apply this action to all files").assertIsNotEnabled()
        captureScreenRoboImage("build/screenshots/file_job_conflict_rename.png")
        composeTestRule.onNodeWithText("Rename").performClick()
        composeTestRule.waitForIdle()
        val conflictResult = result!!
        assertEquals(FileJobConflictAction.RENAME, conflictResult.action)
        assertEquals("notes 2.txt", conflictResult.name)
        assertEquals(false, conflictResult.isAll)
    }

    @Test
    fun skipsFromErrorDialog() {
        var result: Pair<FileJobErrorAction, Boolean>? = null
        val args = FileJobErrorDialogActivity.Args(
            "Error", "Unable to copy “notes.txt”.", null, true, "Retry", "Skip", "Cancel"
        ) { action, isAll -> result = action to isAll }
        ActivityScenario.launch<FileJobErrorDialogActivity>(
            FileJobErrorDialogActivity::class.createIntent().putArgs(args)
        )
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/file_job_error.png")
        composeTestRule.onNodeWithText("Apply this action to all files").performClick()
        composeTestRule.onNodeWithText("Skip").performClick()
        composeTestRule.waitForIdle()
        assertEquals(FileJobErrorAction.NEGATIVE to true, result)
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileaction

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
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
class ArchivePasswordDialogActivityTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Test
    fun requiresPassword() {
        val rootPath = TestDocumentsProvider.install()
        TestDocumentsProvider.addFile("secret.zip", "application/zip", 1024)
        val archivePath = rootPath.resolve("secret.zip").createArchiveRootPath()
        var result: Boolean? = null
        val args = ArchivePasswordDialogActivity.Args(archivePath) { result = it }
        ActivityScenario.launch<ArchivePasswordDialogActivity>(
            ArchivePasswordDialogActivity::class.createIntent().putArgs(args)
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("“secret.zip” is password-protected.").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithText("Password cannot be empty").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/archive_password.png")
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("hunter2")
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, result)
    }
}

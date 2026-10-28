/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.video

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.TestFileContentProvider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoPlayerActivityTest {
    private val temporaryFolder = TemporaryFolder()

    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain.outerRule(AppProviderRule()).around(temporaryFolder).around(composeTestRule)

    @Test
    fun playsVideoOfAnotherApp() {
        TestFileContentProvider.install()
        val uri = TestFileContentProvider.addFile(temporaryFolder.newFile("clip.mp4"), "video/mp4")
        // Like a video opened from another app, with only its content URI.
        ActivityScenario.launch<VideoPlayerActivity>(
            Intent(ApplicationProvider.getApplicationContext(), VideoPlayerActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, "video/mp4")
        )
        composeTestRule.waitUntil {
            composeTestRule.onAllNodesWithText("clip.mp4").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("clip.mp4").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/video_player.png")
    }
}

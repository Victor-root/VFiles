/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.image

import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.TestFileContentProvider
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
class ImageViewerActivityTest {
    private val temporaryFolder = TemporaryFolder()

    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain.outerRule(AppProviderRule()).around(temporaryFolder).around(composeTestRule)

    private fun launch(names: List<String>, position: Int = 0) {
        TestFileContentProvider.install()
        val paths: List<Path> = names.map {
            val file = temporaryFolder.newFile(it)
            val uri = TestFileContentProvider.addFile(file, "image/png")
            Paths.get(java.net.URI.create(uri.toString()))
        }
        val intent = Intent(
            ApplicationProvider.getApplicationContext(), ImageViewerActivity::class.java
        )
        ImageViewerActivity.putExtras(intent, paths, position)
        ActivityScenario.launch<ImageViewerActivity>(intent)
    }

    @Test
    fun showsTitleAndPosition() {
        launch(listOf("a.png", "b.png", "c.png"), 1)
        composeTestRule.onNodeWithText("b.png").assertIsDisplayed()
        composeTestRule.onNodeWithText("2/3").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/image_viewer.png")
    }

    @Test
    fun dpadMovesBetweenImages() {
        launch(listOf("a.png", "b.png", "c.png"))
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithText("2/3").assertIsDisplayed()
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.onNodeWithText("1/3").assertIsDisplayed()
    }

    @Test
    fun deleteAsksForConfirmation() {
        launch(listOf("a.png"))
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.onNodeWithText("Delete “a.png”?").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/image_viewer_delete.png")
    }
}

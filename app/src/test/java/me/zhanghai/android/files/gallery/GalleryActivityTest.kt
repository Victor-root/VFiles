/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.TestMediaStoreProvider
import me.zhanghai.android.files.test.waitForBackgroundWork
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryActivityTest {
    private val temporaryFolder = TemporaryFolder()

    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain.outerRule(AppProviderRule()).around(temporaryFolder).around(composeTestRule)

    @Before
    fun setUp() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        TestMediaStoreProvider.install(temporaryFolder.newFolder())
        GalleryLiveData.reload()
    }

    private fun launch() {
        ActivityScenario.launch(GalleryActivity::class.java)
        waitForText("Today")
    }

    private fun waitForText(text: String) {
        composeTestRule.waitForBackgroundWork {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun showsPhotosByDay() {
        launch()
        composeTestRule.onNodeWithText("Yesterday").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("IMG_today_0.png").assertIsDisplayed()
        composeTestRule.onNodeWithText("1:23").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/gallery.png")
    }

    @Test
    fun showsAlbums() {
        launch()
        composeTestRule.onNodeWithText("Albums").performClick()
        composeTestRule.onNodeWithText("Camera").assertIsDisplayed()
        composeTestRule.onNodeWithText("9 items").assertIsDisplayed()
        composeTestRule.onNodeWithText("Screenshots").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/gallery_albums.png")
        composeTestRule.onNodeWithText("Screenshots").performClick()
        composeTestRule.onNodeWithContentDescription("Screenshot_0.png").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/gallery_album.png")
    }

    @Test
    fun selectsWithLongClick() {
        launch()
        composeTestRule.onNodeWithContentDescription("IMG_today_0.png")
            .performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("IMG_yesterday", substring = true)
            .onFirst()
            .performClick()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        // The header selects its whole day.
        composeTestRule.onNodeWithText("Yesterday").performClick()
        composeTestRule.onNodeWithText("4").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/gallery_selection.png")
    }

    @Test
    fun showsViewer() {
        launch()
        ActivityScenario.launch<GalleryViewerActivity>(
            GalleryViewerActivity::class.createIntent()
                .putArgs(GalleryViewerActivity.Args(null, 1))
        )
        composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/gallery_viewer.png")
        composeTestRule.onNodeWithContentDescription("Details").performClick()
        waitForText("IMG_today_0.png")
        captureScreenRoboImage("build/screenshots/gallery_viewer_details.png")
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.TestMediaStoreProvider
import me.zhanghai.android.files.test.waitForBackgroundWork
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
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
        Settings.EDGE_TO_EDGE.putValue(false)
        TestMediaStoreProvider.install(temporaryFolder.newFolder())
        GalleryLiveData.reload()
    }

    private fun launch(): ActivityScenario<GalleryActivity> =
        ActivityScenario.launch(GalleryActivity::class.java).also { waitForText("Today") }

    private fun launchViewer(): ActivityScenario<GalleryViewerActivity> =
        ActivityScenario.launch(
            GalleryViewerActivity::class.createIntent()
                .putArgs(GalleryViewerActivity.Args(null, 1))
        )

    // Like when opening the gallery with a D-pad on a TV.
    private fun setUpTelevision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun pressKey(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
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
    fun showsPhotosBehindAppBarEdgeToEdge() {
        Settings.EDGE_TO_EDGE.putValue(true)
        launch()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/gallery_edge_to_edge.png")
    }

    @Test
    fun showsAlbums() {
        launch()
        composeTestRule.onNodeWithContentDescription("Albums").performClick()
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
        launchViewer()
        composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/gallery_viewer.png")
        composeTestRule.onNodeWithContentDescription("Details").performClick()
        waitForText("IMG_today_0.png")
        captureScreenRoboImage("build/screenshots/gallery_viewer_details.png")
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun movesWithDpadOnTelevision() {
        setUpTelevision()
        val scenario = launch()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("IMG_today_0.png").assertIsFocused()
        captureScreenRoboImage("build/screenshots/gallery_tv.png")
        // The tabs are on the left.
        pressKey(Key.DirectionLeft)
        composeTestRule.onNodeWithText("Photos").assertIsFocused()
        pressKey(Key.DirectionDown)
        pressKey(Key.DirectionCenter)
        composeTestRule.onNodeWithText("Albums").assertIsFocused()
        pressKey(Key.DirectionRight)
        composeTestRule.onNodeWithText("Camera").assertIsFocused()
        pressKey(Key.DirectionRight)
        composeTestRule.onNodeWithText("Screenshots").assertIsFocused()
        captureScreenRoboImage("build/screenshots/gallery_tv_albums.png")
        // An album focuses its media, and back focuses it again.
        pressKey(Key.DirectionCenter)
        composeTestRule.onNodeWithContentDescription("Screenshot_0.png").assertIsFocused()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Screenshots").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun viewerMovesWithDpadOnTelevision() {
        setUpTelevision()
        val scenario = launchViewer()
        composeTestRule.waitForBackgroundWork {
            composeTestRule.onAllNodesWithContentDescription("Share").fetchSemanticsNodes()
                .isNotEmpty()
        }
        // Down goes to the actions, and back goes back to the photo.
        pressKey(Key.DirectionDown)
        composeTestRule.onNodeWithContentDescription("Share").assertIsFocused()
        pressKey(Key.DirectionRight)
        composeTestRule.onNodeWithContentDescription("Details").assertIsFocused()
        captureScreenRoboImage("build/screenshots/gallery_tv_viewer.png")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Details").assertIsNotFocused()
        scenario.onActivity { assertFalse(it.isFinishing) }
        // The center key hides the bars of a photo.
        pressKey(Key.DirectionCenter)
        composeTestRule.onNodeWithContentDescription("Details").assertDoesNotExist()
    }
}

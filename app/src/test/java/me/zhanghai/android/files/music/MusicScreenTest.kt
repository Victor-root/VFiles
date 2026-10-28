/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.DestinationTestActivity
import me.zhanghai.android.files.test.TestMediaStoreProvider
import me.zhanghai.android.files.test.waitForBackgroundWork
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Assert.assertEquals
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
class MusicScreenTest {
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
        Music.reload()
        // Robolectric can't run the player, which the library doesn't need.
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).declareComponentUnbindable(
            ComponentName(context, MusicPlaybackService::class.java)
        )
    }

    private fun launch(): ActivityScenario<DestinationTestActivity> =
        DestinationTestActivity.launch(MusicDestination).also { waitForText("Shuffle all") }

    private fun waitForText(text: String) {
        composeTestRule.waitForBackgroundWork {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sortsSongsAlbumsAndArtists() {
        val state = MusicState(
            MediaStoreMusic.loadTracks(ApplicationProvider.getApplicationContext())
        )
        assertEquals(
            listOf("Blue Sky", "City Lights", "Falling Rain", "Lost Tape", "Morning Light"),
            state.tracks.map { it.title }
        )
        // The songs of an album are in their order, and the unknown album uses its folder.
        assertEquals(listOf("Blue Sky", "Night Drive", "Recordings"), state.albums.map { it.name })
        assertEquals(
            listOf("Blue Sky", "Morning Light", "Falling Rain"),
            state.albums.first().tracks.map { it.title }
        )
        // Songs of unknown artists are left out of the artists.
        assertEquals(listOf("Echo", "Nova"), state.artists.map { it.name })
        assertEquals(null, state.albums.last().tracks.single().artist)
    }

    @Test
    fun showsSongs() {
        launch()
        composeTestRule.onNodeWithText("Morning Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unknown artist", substring = true).assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/music.png")
    }

    @Test
    fun opensAlbumAndComesBack() {
        launch()
        composeTestRule.onNodeWithContentDescription("Albums").performClick()
        composeTestRule.onNodeWithText("Night Drive").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/music_albums.png")
        composeTestRule.onNodeWithText("Blue Sky").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Falling Rain").assertIsDisplayed()
        composeTestRule.onNodeWithText("City Lights").assertDoesNotExist()
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Night Drive").assertIsDisplayed()
    }

    @Test
    fun showsArtists() {
        launch()
        composeTestRule.onNodeWithContentDescription("Artists").performClick()
        composeTestRule.onNode(hasText("Nova") and hasText("1 album • 3 songs"))
            .assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/music_artists.png")
        composeTestRule.onNodeWithText("Echo").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("City Lights").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun focusesSongsWithDpad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        launch()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Shuffle all").assertIsFocused()
        captureScreenRoboImage("build/screenshots/music_television.png")
    }
}

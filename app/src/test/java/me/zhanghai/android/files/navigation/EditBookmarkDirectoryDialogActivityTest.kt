/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java8.nio.file.Paths
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.valueCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditBookmarkDirectoryDialogActivityTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private lateinit var activity: EditBookmarkDirectoryDialogActivity

    private lateinit var bookmarkDirectory: BookmarkDirectory

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        bookmarkDirectory = BookmarkDirectory(null, Paths.get("/storage/emulated/0/Download"))
        Settings.BOOKMARK_DIRECTORIES.putValue(listOf(bookmarkDirectory))
    }

    private fun launch() {
        ActivityScenario.launch<EditBookmarkDirectoryDialogActivity>(
            Intent(
                ApplicationProvider.getApplicationContext(),
                EditBookmarkDirectoryDialogActivity::class.java
            ).putArgs(EditBookmarkDirectoryDialogActivity.Args(bookmarkDirectory))
        ).onActivity { activity = it }
    }

    @Test
    fun showsBookmarkDirectory() {
        launch()
        composeTestRule.onNodeWithText("Bookmark folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("/storage/emulated/0/Download").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/edit_bookmark_directory_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/edit_bookmark_directory_dark.png")
    }

    @Test
    fun savesNameAndFinishes() {
        launch()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Work")
        composeTestRule.onNodeWithText("OK").performClick()
        assertEquals("Work", Settings.BOOKMARK_DIRECTORIES.valueCompat.single().customName)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun doesNotSaveDefaultNameAsCustomName() {
        launch()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Download")
        composeTestRule.onNodeWithText("OK").performClick()
        assertEquals(null, Settings.BOOKMARK_DIRECTORIES.valueCompat.single().customName)
    }

    @Test
    fun picksPath() {
        launch()
        composeTestRule.onNodeWithText("/storage/emulated/0/Download").performClick()
        val newPath = Paths.get("/storage/emulated/0/Music")
        val shadowActivity = shadowOf(activity)
        val intent = shadowActivity.nextStartedActivityForResult.intent
        assertEquals(FileListActivity::class.java.name, intent.component!!.className)
        assertEquals(bookmarkDirectory.path, intent.extraPath)
        shadowActivity.receiveResult(
            intent, Activity.RESULT_OK, Intent().apply { extraPath = newPath }
        )
        composeTestRule.onNodeWithText("/storage/emulated/0/Music").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()
        assertEquals(newPath, Settings.BOOKMARK_DIRECTORIES.valueCompat.single().path)
    }

    @Test
    fun removesBookmarkDirectory() {
        launch()
        composeTestRule.onNodeWithText("Remove").performClick()
        assertEquals(emptyList<BookmarkDirectory>(), Settings.BOOKMARK_DIRECTORIES.valueCompat)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun cancelKeepsBookmarkDirectory() {
        launch()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Work")
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf(bookmarkDirectory), Settings.BOOKMARK_DIRECTORIES.valueCompat)
        assertTrue(activity.isFinishing)
    }
}

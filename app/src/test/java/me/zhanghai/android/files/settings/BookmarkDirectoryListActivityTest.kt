/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java8.nio.file.Paths
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.EditBookmarkDirectoryDialogActivity
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.leaveTouchMode
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.getArgs
import me.zhanghai.android.files.util.valueCompat
import org.junit.Assert.assertEquals
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
class BookmarkDirectoryListActivityTest {
    private val composeTestRule = createAndroidComposeRule<BookmarkDirectoryListActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private lateinit var bookmarkDirectories: List<BookmarkDirectory>

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        bookmarkDirectories = listOf("Alpha", "Beta", "Gamma").map {
            BookmarkDirectory(null, Paths.get("/storage/emulated/0/$it"))
        }
        Settings.BOOKMARK_DIRECTORIES.putValue(bookmarkDirectories)
    }

    @Test
    fun showsBookmarkDirectories() {
        composeTestRule.onNodeWithText("Bookmark folders").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta").assertIsDisplayed()
        composeTestRule.onNodeWithText("/storage/emulated/0/Beta").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/bookmark_directory_list_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/bookmark_directory_list_dark.png")
    }

    @Test
    fun showsEmptyState() {
        Settings.BOOKMARK_DIRECTORIES.putValue(emptyList())
        composeTestRule.onNodeWithText("No bookmark folders").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/bookmark_directory_list_empty.png")
    }

    @Test
    fun clickOpensEditDialog() {
        composeTestRule.onNodeWithText("Beta").performClick()
        val intent = shadowOf(composeTestRule.activity).nextStartedActivity
        assertEquals(
            EditBookmarkDirectoryDialogActivity::class.java.name, intent.component!!.className
        )
        assertEquals(
            bookmarkDirectories[1],
            intent.extras!!.getArgs<EditBookmarkDirectoryDialogActivity.Args>().bookmarkDirectory
        )
    }

    @Test
    fun addButtonAddsPickedDirectory() {
        composeTestRule.onNodeWithContentDescription("Add bookmark").performClick()
        val shadowActivity = shadowOf(composeTestRule.activity)
        val intent = shadowActivity.nextStartedActivityForResult.intent
        assertEquals(FileListActivity::class.java.name, intent.component!!.className)
        val path = Paths.get("/storage/emulated/0/Delta")
        shadowActivity.receiveResult(
            intent, Activity.RESULT_OK, Intent().apply { extraPath = path }
        )
        composeTestRule.onNodeWithText("Delta").assertIsDisplayed()
        assertEquals(path, Settings.BOOKMARK_DIRECTORIES.valueCompat.last().path)
    }

    @Test
    fun dragHandleReordersBookmarkDirectories() {
        composeTestRule.onNodeWithText("Alpha").performTouchInput {
            down(Offset(width - 28.dp.toPx(), centerY))
            // Past the middle of the next item, but not of the one after.
            val distance = 86.dp.toPx()
            repeat(10) { moveBy(Offset(0f, distance / 10)) }
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(
            listOf("Beta", "Alpha", "Gamma"),
            Settings.BOOKMARK_DIRECTORIES.valueCompat.map { it.name }
        )
    }

    @Test
    fun dpadOpensEditDialog() {
        composeTestRule.runOnUiThread { composeTestRule.activity.leaveTouchMode() }
        composeTestRule.onNodeWithText("Alpha")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        val root = composeTestRule.onRoot()
        root.performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.onNodeWithText("Beta").assertIsFocused()
        root.performKeyInput { pressKey(Key.DirectionCenter) }
        val intent = shadowOf(composeTestRule.activity).nextStartedActivity
        assertEquals(
            bookmarkDirectories[1],
            intent.extras!!.getArgs<EditBookmarkDirectoryDialogActivity.Args>().bookmarkDirectory
        )
    }

}

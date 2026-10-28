/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.core.content.getSystemService
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java8.nio.file.Path
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.TestDocumentsProvider
import me.zhanghai.android.files.test.leaveTouchMode
import me.zhanghai.android.files.test.waitForBackgroundWork
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.extraPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileListActivityTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private lateinit var rootPath: Path

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.ONBOARDING_COMPLETED.putValue(true)
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.FILE_LIST_VIEW_TYPE.putValue(FileViewType.LIST)
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(false)
        Settings.EDGE_TO_EDGE.putValue(false)
        Settings.CREATE_ARCHIVE_TYPE.putValue(ArchiveType.ZIP)
        // Granted, so that the storage access isn't asked for.
        ShadowEnvironment.addExternalDir("external")
        shadowOf(context.getSystemService<AppOpsManager>()).setMode(
            OPSTR_MANAGE_EXTERNAL_STORAGE, Process.myUid(), context.packageName,
            AppOpsManager.MODE_ALLOWED
        )
        rootPath = TestDocumentsProvider.install()
        TestDocumentsProvider.addDirectory("Projects")
        TestDocumentsProvider.addFile("Projects/plan.md", "text/markdown", 512)
        TestDocumentsProvider.addDirectory("Empty")
        TestDocumentsProvider.addFile("notes.txt", "text/plain", 2048)
        TestDocumentsProvider.addFile("photo.jpg", "image/jpeg", 3_500_000)
    }

    private fun launch(intent: Intent = FileListActivity.createViewIntent(rootPath)) =
        ActivityScenario.launchActivityForResult<FileListActivity>(intent).also {
            waitForText("notes.txt")
        }

    private fun waitForText(text: String) {
        composeTestRule.waitForBackgroundWork {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun onMenuButtonOf(name: String): SemanticsNodeInteraction =
        composeTestRule.onNode(
            hasContentDescription("More options") and hasAnyAncestor(hasText(name))
        )

    private fun onTextField(): SemanticsNodeInteraction = composeTestRule.onNode(hasSetTextAction())

    @Test
    fun showsFiles() {
        launch()
        composeTestRule.onNodeWithText("2 folders, 2 files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
        composeTestRule.onNodeWithText("photo.jpg").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list.png")
    }

    private fun addManyFiles() {
        for (i in 1..40) {
            TestDocumentsProvider.addFile("zfile$i.txt", "text/plain", 1024)
        }
    }

    @Test
    fun hidesAppBarWhenScrollingDownEdgeToEdge() {
        Settings.EDGE_TO_EDGE.putValue(true)
        addManyFiles()
        launch()
        composeTestRule.onNodeWithText("notes.txt").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Search").assertIsNotDisplayed()
        composeTestRule.onAllNodesWithText("zfile", substring = true).onFirst()
            .performTouchInput { swipeDown(endY = centerY + 200f) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun keepsAppBarWhenScrollingDownNotEdgeToEdge() {
        addManyFiles()
        launch()
        composeTestRule.onNodeWithText("notes.txt").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun showsFilesInDarkMode() {
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        launch()
        captureScreenRoboImage("build/screenshots/file_list_dark.png")
    }

    @Test
    fun showsFilesInGrid() {
        Settings.FILE_LIST_VIEW_TYPE.putValue(FileViewType.GRID)
        launch()
        composeTestRule.onNodeWithText("photo.jpg").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list_grid.png")
    }

    @Test
    fun switchesToGridFromMenu() {
        launch()
        composeTestRule.onNodeWithContentDescription("View and sort").performClick()
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/file_list_view_sort_menu.png")
        composeTestRule.onNodeWithText("Grid").performClick()
        composeTestRule.waitForIdle()
        assertEquals(FileViewType.GRID, Settings.FILE_LIST_VIEW_TYPE.value)
    }

    @Test
    fun opensFolderAndGoesBack() {
        val scenario = launch()
        composeTestRule.onNodeWithText("Projects").performClick()
        waitForText("plan.md")
        composeTestRule.onNodeWithText("1 file").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list_subfolder.png")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("notes.txt")
    }

    @Test
    fun showsEmptyFolder() {
        launch()
        composeTestRule.onNodeWithText("Empty").performClick()
        waitForText("No files")
        captureScreenRoboImage("build/screenshots/file_list_empty.png")
    }

    @Test
    fun opensImageWithImageViewer() {
        val scenario = launch()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        scenario.onActivity {
            assertEquals(it.packageName, shadowOf(it).nextStartedActivity.`package`)
        }
    }

    @Test
    fun refreshShowsNewFiles() {
        launch()
        composeTestRule.onNodeWithText("Empty").performClick()
        waitForText("No files")
        TestDocumentsProvider.addFile("Empty/added.txt", "text/plain", 1024)
        // The only menu in an empty folder is the one of the app bar.
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Refresh").performClick()
        waitForText("added.txt")
    }

    @Test
    fun selectsFiles() {
        launch()
        composeTestRule.onNodeWithText("notes.txt").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        // While selecting, a click selects too.
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list_selection.png")
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.onNodeWithText("2 folders, 2 files").assertIsDisplayed()
    }

    @Test
    fun cutsFileToPaste() {
        launch()
        composeTestRule.onNodeWithText("notes.txt").performTouchInput { longClick() }
        composeTestRule.onNodeWithContentDescription("Cut").performClick()
        composeTestRule.onNodeWithText("Moving 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Projects").performClick()
        waitForText("plan.md")
        // The files to paste stay while browsing.
        composeTestRule.onNodeWithText("Moving 1").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list_paste.png")
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Moving 1").assertDoesNotExist()
    }

    @Test
    fun validatesNewFolderName() {
        launch()
        composeTestRule.onNodeWithContentDescription("Create").performClick()
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/file_list_speed_dial.png")
        composeTestRule.onNodeWithText("Folder").performClick()
        composeTestRule.onNodeWithText("New folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithText("File name cannot be empty").assertIsDisplayed()
        onTextField().performTextReplacement("Projects")
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithText("A file with this name already exists").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list_new_folder_error.png")
    }

    @Test
    fun renamesWithFileName() {
        launch()
        onMenuButtonOf("notes.txt").performClick()
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/file_list_item_menu.png")
        composeTestRule.onNodeWithText("Rename").performClick()
        onTextField().assert(hasText("notes.txt"))
    }

    @Test
    fun choosesArchiveType() {
        launch()
        onMenuButtonOf("notes.txt").performClick()
        composeTestRule.onNodeWithText("Compress").performClick()
        composeTestRule.onNodeWithText("Create archive").assertIsDisplayed()
        composeTestRule.onNodeWithText(".zip").assertIsSelected()
        composeTestRule.onNodeWithText("Password (optional)").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list_create_archive.png")
        composeTestRule.onNodeWithText(".7z").performClick()
        composeTestRule.onNodeWithText(".zip").assertIsNotSelected()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Password (optional)").assertDoesNotExist()
        assertEquals(ArchiveType.SEVEN_Z, Settings.CREATE_ARCHIVE_TYPE.value)
    }

    @Test
    fun opensDrawer() {
        launch()
        composeTestRule.onNodeWithContentDescription("Open navigation drawer").performClick()
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/file_list_drawer.png")
    }

    @Test
    fun movesWithDpad() {
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        // Folders come first, sorted by name.
        val empty = composeTestRule.onNodeWithText("Empty")
        val projects = composeTestRule.onNodeWithText("Projects")
        empty.requestFocus()
        empty.performKeyInput { pressKey(Key.DirectionDown) }
        projects.assertIsFocused()
        // Right goes to the menu of the file, and left comes back.
        projects.performKeyInput { pressKey(Key.DirectionRight) }
        onMenuButtonOf("Projects").assertIsFocused()
        onMenuButtonOf("Projects").performKeyInput { pressKey(Key.DirectionLeft) }
        projects.assertIsFocused()
        // Left from a file opens the drawer.
        projects.performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/file_list_drawer_dpad.png")
    }

    @Test
    fun movesUpFromFirstFileToNavigationIconWithDpad() {
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        val empty = composeTestRule.onNodeWithText("Empty")
        empty.requestFocus()
        empty.performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.onNodeWithContentDescription("Open navigation drawer").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun movesRightFromFileMenuToSpeedDialWithDpad() {
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        val projects = composeTestRule.onNodeWithText("Projects")
        projects.requestFocus()
        projects.performKeyInput { pressKey(Key.DirectionRight) }
        onMenuButtonOf("Projects").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("Create").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun movesUpFromSpeedDialToFilesWithDpad() {
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        val speedDial = composeTestRule.onNodeWithContentDescription("Create")
        speedDial.requestFocus()
        // Back to the list rather than to the app bar.
        speedDial.performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.onNodeWithText("photo.jpg").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun movesSidewaysFromSpeedDialWithDpad() {
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        val speedDial = composeTestRule.onNodeWithContentDescription("Create")
        speedDial.requestFocus()
        // Nothing is on its right, and the list is on its left, rather than the app bar.
        speedDial.performKeyInput { pressKey(Key.DirectionRight) }
        speedDial.assertIsFocused()
        speedDial.performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.onNodeWithText("photo.jpg").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun focusesNavigationIconInEmptyFolderWithDpad() {
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        val empty = composeTestRule.onNodeWithText("Empty")
        empty.requestFocus()
        empty.performKeyInput { pressKey(Key.DirectionCenter) }
        waitForText("No files")
        val navigationIcon = composeTestRule.onNodeWithContentDescription("Open navigation drawer")
        navigationIcon.assertIsFocused()
        // Left opens the drawer from there too.
        composeTestRule.onNodeWithText("Internal storage").assertIsNotDisplayed()
        navigationIcon.performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Internal storage").assertIsDisplayed()
        navigationIcon.assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun focusesNavigationIconInFolderWithOnlyHiddenFilesWithDpad() {
        TestDocumentsProvider.addDirectory("Silent")
        TestDocumentsProvider.addFile("Silent/.nomedia", "application/octet-stream", 0)
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        val silent = composeTestRule.onNodeWithText("Silent")
        silent.requestFocus()
        silent.performKeyInput { pressKey(Key.DirectionCenter) }
        waitForText("No files")
        // Nothing is shown, so the focus can't go to the list.
        composeTestRule.onNodeWithContentDescription("Open navigation drawer").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun opensSettingsFromDrawerAndComesBackWithDpad() {
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        val projects = composeTestRule.onNodeWithText("Projects")
        projects.requestFocus()
        projects.performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.waitForIdle()
        // At the end of the drawer, rather than of the files.
        composeTestRule.onNode(
            hasScrollToNodeAction() and hasAnyDescendant(hasText("Internal storage"))
        ).performScrollToNode(hasText("Settings"))
        val settings = composeTestRule.onNodeWithText("Settings")
        settings.requestFocus()
        settings.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        // Opened above the files, in the same activity.
        composeTestRule.onNodeWithText("Interface").assertIsDisplayed()
        composeTestRule.onNodeWithText("notes.txt").assertDoesNotExist()
        scenario.onActivity {
            assertFalse(it.isFinishing)
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        // The drawer stayed open, with its item focused before focused again.
        settings.assertIsDisplayed().assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun comesBackToDrawerItemWithDpad() {
        val scenario = launch()
        scenario.onActivity { it.leaveTouchMode() }
        val projects = composeTestRule.onNodeWithText("Projects")
        projects.requestFocus()
        projects.performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasScrollToNodeAction() and hasAnyDescendant(hasText("Internal storage"))
        ).performScrollToNode(hasText("Add storage"))
        val addStorage = composeTestRule.onNodeWithText("Add storage")
        addStorage.requestFocus()
        addStorage.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        // The drawer stays open below the screen opened from the dialog.
        composeTestRule.onNodeWithText("SFTP server").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add SFTP server").assertIsDisplayed()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add storage").assertIsFocused()
    }

    @Test
    fun exitsWithBackPressedTwice() {
        val scenario = launch()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals("Press back again to exit", ShadowToast.getTextOfLatestToast())
        scenario.onActivity {
            assertFalse(it.isFinishing)
            it.onBackPressedDispatcher.onBackPressed()
            assertTrue(it.isFinishing)
        }
    }

    @Test
    fun picksFileForAnotherApp() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setClass(ApplicationProvider.getApplicationContext(), FileListActivity::class.java)
            .setType("text/*")
            .apply { extraPath = rootPath }
        val scenario = launch(intent)
        composeTestRule.onNodeWithText("Select a file").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list_pick.png")
        composeTestRule.onNodeWithText("notes.txt").performClick()
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(rootPath.resolve("notes.txt"), scenario.result.resultData.extraPath)
    }

    @Test
    fun picksWhereToCreateFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setClass(ApplicationProvider.getApplicationContext(), FileListActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, "report.txt")
            .apply { extraPath = rootPath }
        val scenario = launch(intent)
        composeTestRule.onNodeWithText("report.txt").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_list_create_document.png")
        composeTestRule.onNodeWithContentDescription("Save").performClick()
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(rootPath.resolve("report.txt"), scenario.result.resultData.extraPath)
    }

    companion object {
        // AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE is a system API.
        private const val OPSTR_MANAGE_EXTERNAL_STORAGE = "android:manage_external_storage"
    }
}

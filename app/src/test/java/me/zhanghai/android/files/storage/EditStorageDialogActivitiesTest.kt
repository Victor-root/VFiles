/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.file.ExternalStorageUri
import me.zhanghai.android.files.file.asDocumentTreeUri
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditStorageDialogActivitiesTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private val primaryStorageVolume = PrimaryStorageVolume(null, true)
    private val documentTree = DocumentTree(
        1, "Downloads",
        Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload")
            .asDocumentTreeUri()
    )
    private val externalStorageShortcut = ExternalStorageShortcut(
        2, null, ExternalStorageUri("primary", "Android/data")
    )

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.STORAGES.putValue(
            listOf(
                FileSystemRoot(null, false), primaryStorageVolume, documentTree,
                externalStorageShortcut
            )
        )
    }

    @Test
    fun deviceStorageDialogRenamesStorage() {
        val scenario = launch(
            EditDeviceStorageDialogActivity::class.createIntent()
                .putArgs(EditDeviceStorageDialogActivity.Args(primaryStorageVolume))
        )
        val nameNode = composeTestRule.onNode(hasSetTextAction() and hasText("Internal storage"))
        nameNode.assertIsFocused()
        composeTestRule.onNodeWithText("/storage/emulated/0").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_edit_device_storage_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/storage_edit_device_storage_dark.png")
        nameNode.performTextReplacement("Phone")
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Phone", Settings.STORAGES.value[1].customName)
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    @Test
    fun deviceStorageDialogKeepsDefaultNameAsNoCustomName() {
        launch(
            EditDeviceStorageDialogActivity::class.createIntent()
                .putArgs(
                    EditDeviceStorageDialogActivity.Args(PrimaryStorageVolume("Phone", true))
                )
        )
        composeTestRule.onNode(hasSetTextAction() and hasText("Phone"))
            .performTextReplacement("Internal storage")
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        assertEquals(null, Settings.STORAGES.value[1].customName)
    }

    @Test
    fun deviceStorageDialogHidesStorage() {
        launch(
            EditDeviceStorageDialogActivity::class.createIntent()
                .putArgs(EditDeviceStorageDialogActivity.Args(primaryStorageVolume))
        )
        composeTestRule.onNodeWithText("Hide").performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, Settings.STORAGES.value[1].isVisible)
    }

    @Test
    fun documentTreeDialogRemovesStorage() {
        val scenario = launch(
            EditDocumentTreeDialogActivity::class.createIntent()
                .putArgs(EditDocumentTreeDialogActivity.Args(documentTree))
        )
        composeTestRule.onNode(hasSetTextAction() and hasText("Downloads")).assertIsFocused()
        composeTestRule.onNodeWithText(documentTree.uri.value.toString()).assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_edit_document_tree.png")
        composeTestRule.onNodeWithText("Remove").performClick()
        composeTestRule.waitForIdle()
        assertTrue(Settings.STORAGES.value.none { it.id == documentTree.id })
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    @Test
    fun externalStorageShortcutDialogValidatesStorageVolume() {
        val scenario = launch(
            EditExternalStorageShortcutDialogActivity::class.createIntent()
                .putArgs(EditExternalStorageShortcutDialogActivity.Args(externalStorageShortcut))
        )
        val rootIdNode = composeTestRule.onNode(hasSetTextAction() and hasText("primary"))
        rootIdNode.performTextReplacement("")
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithText("Enter a storage volume").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_edit_external_storage_shortcut_error.png")
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
        composeTestRule.onNode(hasSetTextAction() and hasText("Storage volume"))
            .performTextReplacement("1234-5678")
        composeTestRule.onNodeWithText("Enter a storage volume").assertDoesNotExist()
        composeTestRule.onNode(hasSetTextAction() and hasText("Android/data"))
            .performTextReplacement("/Android/obb")
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        val storage = Settings.STORAGES.value[3] as ExternalStorageShortcut
        assertEquals(ExternalStorageUri("1234-5678", "Android/obb"), storage.uri)
        // The name was the default one for the old path, it's kept as a custom name.
        assertEquals("data", storage.customName)
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    private fun launch(intent: Intent): ActivityScenario<Activity> =
        ActivityScenario.launchActivityForResult(intent)
}

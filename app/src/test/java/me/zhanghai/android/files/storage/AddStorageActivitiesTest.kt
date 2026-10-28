/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.ExternalStorageUri
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.getArgs
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
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddStorageActivitiesTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private val androidDataUri = ExternalStorageUri("primary", "Android/data")

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.STORAGES.putValue(emptyList())
    }

    @Test
    fun addStorageDialogStartsChosenType() {
        val scenario = launch(AddStorageDialogActivity::class.createIntent())
        composeTestRule.onNodeWithText("Android/data").assertIsDisplayed()
        composeTestRule.onNodeWithText("External storage").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/storage_add_storage_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/storage_add_storage_dark.png")
        composeTestRule.onNodeWithText("SFTP server").performClick()
        lateinit var startedIntent: Intent
        scenario.onActivity { startedIntent = shadowOf(it).nextStartedActivity }
        assertEquals(EditSftpServerActivity::class.java.name, startedIntent.component!!.className)
        assertEquals(null, startedIntent.extras!!.getArgs<EditSftpServerActivity.Args>().server)
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    @Test
    fun addExternalStorageShortcutNeedsDocumentsUi() {
        installDocumentsUi(canViewDirectory = false)
        val scenario = launch(externalStorageShortcutIntent())
        composeTestRule.waitForIdle()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertEquals(
            ApplicationProvider.getApplicationContext<Context>()
                .getString(R.string.activity_not_found),
            ShadowToast.getTextOfLatestToast()
        )
        assertTrue(Settings.STORAGES.valueCompat.isEmpty())
    }

    @Test
    fun addExternalStorageShortcutAddsShortcut() {
        installDocumentsUi(canViewDirectory = true)
        val scenario = launch(externalStorageShortcutIntent())
        composeTestRule.waitForIdle()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val storage = Settings.STORAGES.valueCompat.single() as ExternalStorageShortcut
        assertEquals("Android/data", storage.customName)
        assertEquals(androidDataUri, storage.uri)
        // The app can't grant the files app of the system a permission it doesn't have itself.
        val flags = storage.createIntent().flags
        assertEquals(0, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun addDocumentTreeAddsPickedTree() {
        val scenario = launch(AddDocumentTreeActivity::class.createIntent())
        composeTestRule.waitForIdle()
        val treeUri =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload")
        scenario.onActivity {
            val shadowActivity = shadowOf(it)
            val pickerIntent = shadowActivity.nextStartedActivityForResult.intent
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, pickerIntent.action)
            shadowActivity.receiveResult(
                pickerIntent, Activity.RESULT_OK, Intent().setData(treeUri)
            )
        }
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val storage = Settings.STORAGES.valueCompat.single() as DocumentTree
        assertEquals(treeUri, storage.uri.value)
        assertEquals(null, storage.customName)
    }

    // The shortcut is opened with the app holding the permission to manage documents.
    private fun installDocumentsUi(canViewDirectory: Boolean) {
        val packageManager = shadowOf(
            ApplicationProvider.getApplicationContext<Context>().packageManager
        )
        packageManager.installPackage(
            PackageInfo().apply {
                packageName = DOCUMENTS_UI_PACKAGE
                applicationInfo = ApplicationInfo().apply { packageName = DOCUMENTS_UI_PACKAGE }
                requestedPermissions = arrayOf(Manifest.permission.MANAGE_DOCUMENTS)
                requestedPermissionsFlags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED)
            }
        )
        if (canViewDirectory) {
            val activity =
                ComponentName(DOCUMENTS_UI_PACKAGE, "$DOCUMENTS_UI_PACKAGE.FilesActivity")
            packageManager.addActivityIfNotPresent(activity)
            packageManager.addIntentFilterForActivity(
                activity,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataType(DocumentsContract.Document.MIME_TYPE_DIR)
                }
            )
        }
    }

    private fun externalStorageShortcutIntent(): Intent =
        AddExternalStorageShortcutActivity::class.createIntent().putArgs(
            AddExternalStorageShortcutActivity.Args(
                R.string.storage_add_storage_android_data, androidDataUri
            )
        )

    private fun launch(intent: Intent): ActivityScenario<Activity> =
        ActivityScenario.launchActivityForResult(intent)

    companion object {
        private const val DOCUMENTS_UI_PACKAGE = "com.android.documentsui"
    }
}

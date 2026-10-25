/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.attribute.FileTime
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.getCollationKeyForFileName
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.fileproperties.apk.PermissionListDialog
import me.zhanghai.android.files.fileproperties.permission.PrincipalItem
import me.zhanghai.android.files.fileproperties.permission.SetPrincipalDialog
import me.zhanghai.android.files.fileproperties.permission.SetPrincipalViewModel
import me.zhanghai.android.files.provider.common.PosixFileMode
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.linux.LinuxFileAttributes
import me.zhanghai.android.files.provider.linux.LinuxFileKey
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.TestFileContentProvider
import me.zhanghai.android.files.test.waitForBackgroundWork
import me.zhanghai.android.files.theme.AppTheme
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.Success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.net.URI
import java.text.Collator

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilePropertiesDialogTest {
    private val temporaryFolder = TemporaryFolder()

    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain.outerRule(AppProviderRule()).around(temporaryFolder).around(composeTestRule)

    @Before
    fun setUp() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        TestFileContentProvider.install()
    }

    @Test
    fun textFileShowsBasicAndChecksumTabs() {
        val file = loadFileItem(addTextFile("notes.txt", "Hello"))
        var isDismissed = false
        setContent { FilePropertiesDialog(file) { isDismissed = true } }
        composeTestRule.onNodeWithText("“notes.txt” properties").assertIsDisplayed()
        composeTestRule.onNodeWithText("Basic").assertIsSelected()
        composeTestRule.onNode(hasText("Name") and hasText("notes.txt")).assertIsDisplayed()
        composeTestRule.onNodeWithText("5 bytes").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_properties_basic_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/file_properties_basic_dark.png")

        composeTestRule.onNodeWithText("Checksums").performClick()
        waitForText(MD5)
        composeTestRule.onNode(hasText("SHA-1") and hasText(SHA1)).assertIsDisplayed()
        val compareNode = composeTestRule.onNode(hasSetTextAction() and hasText("Compare"))
        compareNode.performTextReplacement(" ${MD5.lowercase()} ")
        composeTestRule.onNodeWithText("Matches MD5 checksum").assertIsDisplayed()
        compareNode.performTextReplacement(SHA1.take(6))
        composeTestRule.onNodeWithText("Matches prefix of SHA-1 checksum").assertIsDisplayed()
        compareNode.performTextReplacement("123")
        composeTestRule.onNodeWithText("No matching checksum found").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_properties_checksum_dark.png")

        composeTestRule.onNodeWithText("OK").performClick()
        assertTrue(isDismissed)
    }

    @Test
    fun tabsAreReachableWithDpad() {
        val file = loadFileItem(addTextFile("notes.txt", "Hello"))
        // Like after a key press, so that clickables in the dialog window can take focus.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        setContent { FilePropertiesDialog(file) {} }
        val basicTabNode = composeTestRule.onNodeWithText("Basic")
        basicTabNode.performSemanticsAction(SemanticsActions.RequestFocus)
        basicTabNode.performKeyInput { pressKey(Key.DirectionRight) }
        val checksumTabNode = composeTestRule.onNodeWithText("Checksums")
        checksumTabNode.assertIsFocused()
        checksumTabNode.performKeyInput { pressKey(Key.DirectionCenter) }
        checksumTabNode.assertIsSelected()
        waitForText(MD5)
        checksumTabNode.performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.onNode(hasText("CRC32") and hasText(CRC32)).assertIsFocused()
        captureScreenRoboImage("build/screenshots/file_properties_dpad.png")
    }

    @Test
    fun imageFileShowsDimensions() {
        val imageFile = temporaryFolder.newFile("photo.png")
        imageFile.outputStream().use {
            Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val file = loadFileItem(TestFileContentProvider.addFile(imageFile, "image/png").toPath())
        setContent { FilePropertiesDialog(file) {} }
        composeTestRule.onNodeWithText("Image").performClick()
        waitForText("20 × 10")
        captureScreenRoboImage("build/screenshots/file_properties_image.png")
    }

    @Test
    fun checksumTabShowsError() {
        val path = "content://me.zhanghai.android.files.test/missing.bin".toPath()
        setContent { FilePropertiesDialog(posixFileItem(path, PosixFileType.REGULAR_FILE)) {} }
        composeTestRule.onNodeWithText("Checksums").performClick()
        waitForText("NoSuchElementException", substring = true)
        captureScreenRoboImage("build/screenshots/file_properties_checksum_error.png")
    }

    @Test
    fun anotherFileGetsItsOwnProperties() {
        val firstFile = loadFileItem(addTextFile("first.txt", "1"))
        val secondFile = loadFileItem(addTextFile("second.txt", "2"))
        var file by mutableStateOf(firstFile)
        setContent { FilePropertiesDialog(file) {} }
        composeTestRule.onNode(hasText("Name") and hasText("first.txt")).assertIsDisplayed()
        file = secondFile
        composeTestRule.onNode(hasText("Name") and hasText("second.txt")).assertIsDisplayed()
    }

    @Test
    fun permissionTabChangesMode() {
        val file = posixFileItem(addTextFile("folder", ""), PosixFileType.DIRECTORY)
        setContent { FilePropertiesDialog(file) {} }
        composeTestRule.onNodeWithText("Permissions").performClick()
        composeTestRule.onNode(hasText("Owner") and hasText("root (0)")).assertIsDisplayed()
        composeTestRule.onNode(hasText("Group") and hasText("system (1000)")).assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_properties_permission.png")

        composeTestRule.onNodeWithText("rwxr-xr-x (0755)").performClick()
        composeTestRule.onNodeWithText("Change mode").assertIsDisplayed()
        val uppercaseXNode =
            composeTestRule.onNodeWithText("Don’t add “Execute” for enclosed files")
        uppercaseXNode.assertIsNotEnabled()
        composeTestRule.onNodeWithText("Read, Write, and Search").performClick()
        composeTestRule.onNodeWithText("Write").performClick()
        composeTestRule.onNode(hasText("Owner") and hasText("Read and Search")).assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_properties_set_mode.png")
        composeTestRule.onNodeWithText("Apply to enclosed files").performClick()
        uppercaseXNode.assertIsEnabled()
        clickNestedDialogOk()
        assertFileJobStarted()
    }

    @Test
    fun permissionTabChangesSeLinuxContext() {
        openSeLinuxContextDialog()
        captureScreenRoboImage("build/screenshots/file_properties_set_selinux_context.png")
        composeTestRule.onNode(hasSetTextAction() and hasText("SELinux Context"))
            .performTextReplacement("u:object_r:app_data_file:s0")
        clickNestedDialogOk()
        assertFileJobStarted()
    }

    @Test
    fun unchangedSeLinuxContextIsNotSet() {
        openSeLinuxContextDialog()
        clickNestedDialogOk()
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun setPrincipalDialogFiltersAndPicksPrincipal() {
        val file = posixFileItem(addTextFile("folder", ""), PosixFileType.DIRECTORY)
        val principals = listOf(
            PrincipalItem(0, "root", emptyList(), emptyList()),
            PrincipalItem(1000, "system", emptyList(), emptyList()),
            PrincipalItem(10057, "u0_a57", emptyList(), listOf("Example"))
        )
        var picked: Pair<PrincipalItem, Boolean>? = null
        setContent {
            SetPrincipalDialog(
                file = file,
                titleRes = R.string.file_properties_permission_set_owner_title,
                principalIconRes = R.drawable.person_icon_control_normal_24dp,
                principalId = 0,
                createViewModel = {
                    object : SetPrincipalViewModel(MutableLiveData(Success(principals)), 0) {}
                },
                setPrincipal = { _, principal, recursive -> picked = principal to recursive },
                onDismissRequest = {}
            )
        }
        composeTestRule.onNodeWithText("root (0)").assertIsSelected()
        composeTestRule.onNodeWithText("Example").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_properties_set_owner.png")
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Exa")
        composeTestRule.onNodeWithText("root (0)").assertDoesNotExist()
        composeTestRule.onNodeWithText("u0_a57 (10057)").performClick()
        composeTestRule.onNodeWithText("Apply to enclosed files").performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        assertEquals(principals[2] to true, picked)
    }

    @Test
    fun permissionListDialogCopiesNameOnLongClick() {
        setContent {
            PermissionListDialog(arrayOf("com.example.permission.FIRST", "com.example.SECOND")) {}
        }
        waitForText("com.example.SECOND")
        composeTestRule.onNodeWithText("2 permissions requested").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/file_properties_apk_permissions.png")
        composeTestRule.onNodeWithText("com.example.SECOND").performTouchInput { longClick() }
        assertEquals("com.example.SECOND", clipboardManager.primaryClip!!.getItemAt(0).text)
    }

    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent { AppTheme { content() } }
    }

    private fun addTextFile(name: String, text: String): Path {
        val file = temporaryFolder.newFile(name).apply { writeText(text) }
        return TestFileContentProvider.addFile(file, "text/plain").toPath()
    }

    private fun Uri.toPath(): Path = toString().toPath()

    private fun String.toPath(): Path = Paths.get(URI.create(this))

    private fun loadFileItem(path: Path): FileItem = path.loadFileItem()

    private fun posixFileItem(path: Path, type: PosixFileType): FileItem {
        val time = FileTime.fromMillis(0)
        val attributes = LinuxFileAttributes(
            time, time, time, type, 0, LinuxFileKey(1, 1), PosixUser(0, "root".toByteString()),
            PosixGroup(1000, "system".toByteString()), PosixFileMode.fromInt(0b111101101),
            SELINUX_CONTEXT.toByteString()
        )
        val mimeType = if (type == PosixFileType.DIRECTORY) {
            MimeType.DIRECTORY
        } else {
            MimeType.GENERIC
        }
        return FileItem(
            path, Collator.getInstance().getCollationKeyForFileName(path.name), attributes, null,
            null, false, mimeType
        )
    }

    private fun openSeLinuxContextDialog() {
        val file = posixFileItem(addTextFile("notes.txt", ""), PosixFileType.REGULAR_FILE)
        setContent { FilePropertiesDialog(file) {} }
        composeTestRule.onNodeWithText("Permissions").performClick()
        composeTestRule.onNodeWithText(SELINUX_CONTEXT).performClick()
        composeTestRule.onNode(
            hasSetTextAction() and hasText("SELinux Context") and hasText(SELINUX_CONTEXT)
        )
            .assertIsFocused()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        composeTestRule.waitForBackgroundWork {
            composeTestRule.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Clicks OK in the dialog shown over the properties dialog. */
    private fun clickNestedDialogOk() {
        composeTestRule.onAllNodesWithText("OK").onLast().performClick()
    }

    private fun assertFileJobStarted() {
        assertEquals(
            FileJobService::class.java.name,
            shadowOf(application).nextStartedService.component!!.className
        )
    }

    companion object {
        private const val CRC32 = "F7D18982"
        private const val MD5 = "8B1A9953C4611296A827ABF8C47804D7"
        private const val SHA1 = "F7FF9E8B7BB2E09B70935A5D785E0CC5D9D0ABF0"
        private const val SELINUX_CONTEXT = "u:object_r:system_file:s0"
    }
}

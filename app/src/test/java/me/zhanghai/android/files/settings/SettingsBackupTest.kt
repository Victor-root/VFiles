/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import androidx.core.content.edit
import java8.nio.file.Paths
import me.zhanghai.android.files.filelist.FileSortOptions
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.test.AppProviderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class SettingsBackupTest {
    @get:Rule
    val appProviderRule = AppProviderRule()

    private val preferences by lazy { getSettingSharedPreferences(null) }
    private val pathPreferences by lazy {
        getSettingSharedPreferences(PathSettings.NAME_SUFFIX)
    }

    private fun backUp(password: String = PASSWORD): ByteArray =
        ByteArrayOutputStream().also { SettingsBackup.write(it, password) }.toByteArray()

    private fun restore(bytes: ByteArray, password: String = PASSWORD) {
        SettingsBackup.restore(bytes.inputStream(), password)
    }

    @Test
    fun restoresEveryKindOfValueInEveryFile() {
        preferences.edit(commit = true) {
            putBoolean("boolean", true)
            putFloat("float", 1.5f)
            putInt("int", 42)
            putLong("long", Long.MAX_VALUE)
            putString("string", "é\"\n")
            putStringSet("set", setOf("a", "b"))
        }
        pathPreferences.edit(commit = true) { putString("/storage/a", "path") }
        val backup = backUp()

        preferences.edit(commit = true) {
            clear()
            putString("added later", "gone")
        }
        pathPreferences.edit(commit = true) { clear() }
        restore(backup)

        assertEquals(true, preferences.getBoolean("boolean", false))
        assertEquals(1.5f, preferences.getFloat("float", 0f))
        assertEquals(42, preferences.getInt("int", 0))
        assertEquals(Long.MAX_VALUE, preferences.getLong("long", 0))
        assertEquals("é\"\n", preferences.getString("string", null))
        assertEquals(setOf("a", "b"), preferences.getStringSet("set", null))
        assertFalse(preferences.contains("added later"))
        assertEquals("path", pathPreferences.getString("/storage/a", null))
    }

    @Test
    fun restoresParcelableValues() {
        val sortOptions = FileSortOptions(
            FileSortOptions.By.SIZE, FileSortOptions.Order.DESCENDING, false
        )
        val bookmark = BookmarkDirectory("Name", Paths.get("/storage/a"))
        Settings.FILE_LIST_SORT_OPTIONS.putValue(sortOptions)
        Settings.BOOKMARK_DIRECTORIES.putValue(listOf(bookmark))
        val backup = backUp()

        Settings.FILE_LIST_SORT_OPTIONS.putValue(
            FileSortOptions(FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true)
        )
        Settings.BOOKMARK_DIRECTORIES.putValue(emptyList())
        restore(backup)

        assertEquals(sortOptions, Settings.FILE_LIST_SORT_OPTIONS.value)
        assertEquals("Name", Settings.BOOKMARK_DIRECTORIES.value.single().name)
    }

    @Test
    fun hidesContentOfFile() {
        preferences.edit(commit = true) { putString("string", "server password") }
        assertFalse(String(backUp(), Charsets.ISO_8859_1).contains("server password"))
    }

    @Test
    fun changesNothingWithWrongPassword() {
        preferences.edit(commit = true) { putString("string", "before") }
        val backup = backUp()
        preferences.edit(commit = true) { putString("string", "after") }

        assertThrows(WrongSettingsBackupPasswordException::class.java) {
            restore(backup, "wrong")
        }
        assertEquals("after", preferences.getString("string", null))
    }

    @Test
    fun detectsDamagedFile() {
        val backup = backUp()
        backup[backup.size - 1] = (backup.last() + 1).toByte()
        assertThrows(WrongSettingsBackupPasswordException::class.java) { restore(backup) }
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(InvalidSettingsBackupException::class.java) {
            restore("not a backup".toByteArray())
        }
        assertThrows(InvalidSettingsBackupException::class.java) { restore(ByteArray(0)) }
    }

    private companion object {
        const val PASSWORD = "password"
    }
}

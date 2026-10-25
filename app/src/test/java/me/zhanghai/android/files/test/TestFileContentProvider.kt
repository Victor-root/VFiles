/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.robolectric.Robolectric
import java.io.File

/**
 * Serves files as content URIs, since the app's Linux file system needs a native library that
 * isn't built for the JVM.
 */
class TestFileContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = uri.file
        val columns = projection?.takeIf { it.isNotEmpty() }
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map {
                    when (it) {
                        OpenableColumns.DISPLAY_NAME -> file.name
                        OpenableColumns.SIZE -> file.length()
                        else -> null
                    }
                }
            )
        }
    }

    override fun getType(uri: Uri): String? = files[uri.lastPathSegment]?.second

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(uri.file, ParcelFileDescriptor.parseMode(mode))

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private val Uri.file: File
        get() = files.getValue(lastPathSegment!!).first

    companion object {
        private const val AUTHORITY = "me.zhanghai.android.files.test"

        private val files = mutableMapOf<String, Pair<File, String>>()

        fun install() {
            Robolectric.buildContentProvider(TestFileContentProvider::class.java).create(AUTHORITY)
        }

        /** Returns the content URI of [file] after [install]. */
        fun addFile(file: File, mimeType: String): Uri {
            files[file.name] = file to mimeType
            return Uri.parse("content://$AUTHORITY/${file.name}")
        }
    }
}

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
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java8.nio.file.Path
import me.zhanghai.android.files.provider.document.createDocumentTreeRootPath
import org.robolectric.Robolectric
import java.io.File

/**
 * An in-memory documents provider, whose tree can be browsed like any folder of the app: the Linux
 * file system needs a native library that doesn't run on the JVM. Document IDs are the paths of
 * the documents from the root, like "root/Music/song.mp3".
 */
class TestDocumentsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    // DocumentsProvider can't be used because Robolectric queries it in a form it doesn't support,
    // so the URIs of DocumentsContract are handled here.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val documentId = DocumentsContract.getDocumentId(uri)
        return MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            if (uri.lastPathSegment == CHILDREN_PATH_SEGMENT) {
                for ((childDocumentId, document) in documents) {
                    if (childDocumentId.substringBeforeLast('/', "") == documentId) {
                        addDocument(childDocumentId, document)
                    }
                }
            } else {
                documents[documentId]?.let { addDocument(documentId, it) }
            }
        }
    }

    override fun getType(uri: Uri): String? =
        documents[DocumentsContract.getDocumentId(uri)]?.mimeType

    private fun MatrixCursor.addDocument(documentId: String, document: TestDocument) {
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, documentId.substringAfterLast('/'))
            .add(Document.COLUMN_MIME_TYPE, document.mimeType)
            .add(Document.COLUMN_SIZE, document.size)
            .add(Document.COLUMN_LAST_MODIFIED, document.lastModified)
            .add(
                Document.COLUMN_FLAGS,
                if (document.mimeType == Document.MIME_TYPE_DIR) {
                    Document.FLAG_DIR_SUPPORTS_CREATE
                } else {
                    Document.FLAG_SUPPORTS_WRITE
                } or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
            )
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val document = documents.getValue(DocumentsContract.getDocumentId(uri))
        val file = File.createTempFile("document", null).apply {
            writeBytes(ByteArray(document.size.toInt()))
            deleteOnExit()
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private class TestDocument(val mimeType: String, val size: Long, val lastModified: Long)

    companion object {
        private const val AUTHORITY = "me.zhanghai.android.files.test.documents"
        private const val ROOT_ID = "root"
        private const val CHILDREN_PATH_SEGMENT = "children"

        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
        )

        // 2026-01-02 03:04:05 UTC, so that the dates shown don't depend on when tests run.
        private const val LAST_MODIFIED = 1767323045000L

        private val documents = sortedMapOf<String, TestDocument>()

        /**
         * Installs an empty provider, and returns the path of its root folder. [documentId] of the
         * methods below are relative to it.
         */
        fun install(): Path {
            documents.clear()
            documents[ROOT_ID] = TestDocument(Document.MIME_TYPE_DIR, 0, LAST_MODIFIED)
            Robolectric.buildContentProvider(TestDocumentsProvider::class.java).create(AUTHORITY)
            return DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
                .createDocumentTreeRootPath()
        }

        fun addDirectory(path: String) {
            documents["$ROOT_ID/$path"] = TestDocument(Document.MIME_TYPE_DIR, 0, LAST_MODIFIED)
        }

        fun addFile(path: String, mimeType: String, size: Long) {
            documents["$ROOT_ID/$path"] = TestDocument(mimeType, size, LAST_MODIFIED)
        }
    }
}

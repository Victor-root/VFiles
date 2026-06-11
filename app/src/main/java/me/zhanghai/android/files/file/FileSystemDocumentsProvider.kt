/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.common.createDirectory
import me.zhanghai.android.files.provider.common.createFile
import me.zhanghai.android.files.provider.common.delete
import me.zhanghai.android.files.provider.common.exists
import me.zhanghai.android.files.provider.common.isDirectory
import me.zhanghai.android.files.provider.common.moveTo
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.withoutPenaltyDeathOnNetwork
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI

/**
 * A [DocumentsProvider] that exposes the whole file system MaterialFiles can browse - not only local
 * storage but also SMB/FTP/SFTP/WebDAV servers and archives - so that other apps can pick a tree or
 * document from any of them via the Storage Access Framework.
 *
 * A document is identified by the URI of its [Path] (e.g. `file:///...`, `smb://...`), which round
 * trips through [Paths.get]. Opening a document is delegated to [FileProvider], which already returns
 * a real file descriptor for local files and a seekable proxy descriptor for everything else.
 */
class FileSystemDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val context = context!!
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        for (storage in Settings.STORAGES.valueCompat) {
            if (!storage.isVisible) {
                continue
            }
            val path = storage.path ?: continue
            try {
                var flags = DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                if (!path.fileSystem.isReadOnly) {
                    flags = flags or DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                }
                cursor.newRow()
                    .add(DocumentsContract.Root.COLUMN_ROOT_ID, path.documentId)
                    .add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, path.documentId)
                    .add(DocumentsContract.Root.COLUMN_TITLE, storage.getName(context))
                    .add(DocumentsContract.Root.COLUMN_FLAGS, flags)
                    .add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.launcher_icon)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        cursor.includeDocument(documentId.toPath())
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parent = parentDocumentId.toPath()
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        try {
            StrictMode::class.withoutPenaltyDeathOnNetwork {
                parent.newDirectoryStream().use { directoryStream ->
                    for (path in directoryStream) {
                        cursor.includeDocument(path)
                    }
                }
            }
        } catch (e: IOException) {
            throw e.toFileNotFoundException()
        }
        context?.let {
            cursor.setNotificationUri(
                it.contentResolver,
                DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId)
            )
        }
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        try {
            val parent = parentDocumentId.toPath()
            val child = documentId.toPath()
            child != parent && child.startsWith(parent)
        } catch (e: Exception) {
            false
        }

    override fun getDocumentType(documentId: String): String {
        val path = documentId.toPath()
        return try {
            StrictMode::class.withoutPenaltyDeathOnNetwork {
                if (path.isDirectory()) {
                    DocumentsContract.Document.MIME_TYPE_DIR
                } else {
                    MimeType.guessFromPath(path.toString()).value
                }
            }
        } catch (e: IOException) {
            MimeType.guessFromPath(path.toString()).value
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        // Reuse FileProvider, which already opens any Path - a real file descriptor for local files
        // and a seekable proxy descriptor (via StorageManager) for remote and archive paths.
        val uri = documentId.toPath().fileProviderUri
        return context!!.contentResolver.openFileDescriptor(uri, mode, signal)
            ?: throw FileNotFoundException(documentId)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val child = parentDocumentId.toPath().getUniqueChildPath(displayName)
        try {
            StrictMode::class.withoutPenaltyDeathOnNetwork {
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    child.createDirectory()
                } else {
                    child.createFile()
                }
            }
        } catch (e: IOException) {
            throw e.toFileNotFoundException()
        }
        notifyChildrenChanged(parentDocumentId)
        return child.documentId
    }

    override fun deleteDocument(documentId: String) {
        val path = documentId.toPath()
        try {
            StrictMode::class.withoutPenaltyDeathOnNetwork { path.delete() }
        } catch (e: IOException) {
            throw e.toFileNotFoundException()
        }
        path.parent?.let { notifyChildrenChanged(it.documentId) }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val path = documentId.toPath()
        val parent = path.parent ?: throw FileNotFoundException("Cannot rename root: $documentId")
        val newPath = parent.getUniqueChildPath(displayName)
        try {
            StrictMode::class.withoutPenaltyDeathOnNetwork { path.moveTo(newPath) }
        } catch (e: IOException) {
            throw e.toFileNotFoundException()
        }
        notifyChildrenChanged(parent.documentId)
        return newPath.documentId
    }

    private fun MatrixCursor.includeDocument(path: Path) {
        val attributes = try {
            StrictMode::class.withoutPenaltyDeathOnNetwork {
                path.readAttributes(BasicFileAttributes::class.java)
            }
        } catch (e: IOException) {
            throw e.toFileNotFoundException()
        }
        val isDirectory = attributes.isDirectory
        val mimeType = if (isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            MimeType.guessFromPath(path.toString()).value
        }
        var flags = 0
        if (!path.fileSystem.isReadOnly) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            flags = flags or if (isDirectory) {
                DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            } else {
                DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            }
        }
        newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, path.documentId)
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, path.displayName)
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            .add(DocumentsContract.Document.COLUMN_SIZE, attributes.size())
            .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, attributes.lastModifiedTime().toMillis())
            .add(DocumentsContract.Document.COLUMN_FLAGS, flags)
    }

    private fun notifyChildrenChanged(parentDocumentId: String) {
        val context = context ?: return
        context.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId), null
        )
    }

    private fun Path.getUniqueChildPath(displayName: String): Path {
        val child = resolve(displayName)
        if (!child.existsWithoutPenalty()) {
            return child
        }
        val lastDot = displayName.lastIndexOf('.')
        val baseName = if (lastDot > 0) displayName.substring(0, lastDot) else displayName
        val extension = if (lastDot > 0) displayName.substring(lastDot) else ""
        var index = 1
        while (true) {
            val candidate = resolve("$baseName ($index)$extension")
            if (!candidate.existsWithoutPenalty()) {
                return candidate
            }
            index++
        }
    }

    private fun Path.existsWithoutPenalty(): Boolean =
        try {
            StrictMode::class.withoutPenaltyDeathOnNetwork { exists() }
        } catch (e: Exception) {
            false
        }

    private val Path.displayName: String
        get() = fileName?.toString() ?: toString()

    private fun String.toPath(): Path = Paths.get(URI.create(this))

    private fun IOException.toFileNotFoundException(): FileNotFoundException {
        if (this is FileNotFoundException) {
            return this
        }
        return FileNotFoundException(message).also { it.initCause(this) }
    }
}

private val AUTHORITY = BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY

private val DEFAULT_ROOT_PROJECTION = arrayOf(
    DocumentsContract.Root.COLUMN_ROOT_ID,
    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
    DocumentsContract.Root.COLUMN_TITLE,
    DocumentsContract.Root.COLUMN_FLAGS,
    DocumentsContract.Root.COLUMN_ICON
)

private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_SIZE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    DocumentsContract.Document.COLUMN_FLAGS
)

/** The document ID identifying [this] path within [FileSystemDocumentsProvider]. */
val Path.documentId: String
    get() = toUri().toString()

/** A tree document URI for [this] path, suitable for returning from `ACTION_OPEN_DOCUMENT_TREE`. */
val Path.treeDocumentUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(AUTHORITY, documentId)

/** A single-document URI for [this] path, suitable for returning from `ACTION_OPEN_DOCUMENT`. */
val Path.documentUri: Uri
    get() = DocumentsContract.buildDocumentUri(AUTHORITY, documentId)

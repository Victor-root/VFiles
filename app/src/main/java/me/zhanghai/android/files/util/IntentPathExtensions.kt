/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.compat.directoryCompat
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.uuidCompat
import me.zhanghai.android.files.storage.StorageVolumeListLiveData
import me.zhanghai.android.files.storage.createOrLog
import java.io.File
import java.io.Serializable
import java.net.URI

private const val EXTRA_PATH_URI = "${BuildConfig.APPLICATION_ID}.extra.PATH_URI"

var Intent.extraPath: Path?
    get() {
        val extraPathUri = getStringExtra(EXTRA_PATH_URI)
        extraPathUri?.let { URI::class.createOrLog(it) }?.let { return Paths.get(it) }
        data?.toPathOrNull()?.let { return it }
        val extraInitialUri = getParcelableExtraSafe<Uri>(DocumentsContractCompat.EXTRA_INITIAL_URI)
        extraInitialUri?.toPathOrNull()?.let { return it }
        val extraAbsolutePath = getStringExtra("org.openintents.extra.ABSOLUTE_PATH")
            ?.takeIfNotEmpty()
        extraAbsolutePath?.let { return Paths.get(it) }
        return null
    }
    set(value) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        // We cannot put URI into intent here either, because ShortcutInfo uses PersistableBundle
        // which doesn't support Serializable.
        putExtra(EXTRA_PATH_URI, value?.toUri()?.toString())
    }

val Intent.saveAsPath: Path?
    get() {
        val uri =
            when (action) {
                Intent.ACTION_VIEW -> data
                Intent.ACTION_SEND -> getParcelableExtraSafe(Intent.EXTRA_STREAM) as? Uri
                else -> null
            }
        return uri?.toPathOrNull()
    }

private fun Uri.toPathOrNull(): Path? =
    when (scheme) {
        ContentResolver.SCHEME_FILE, null -> path?.takeIfNotEmpty()?.let { Paths.get(it) }
        ContentResolver.SCHEME_CONTENT -> {
            // A content:// URI resolves to a ContentPath, which is a single stream that cannot be
            // listed as a directory. When it's an ExternalStorageProvider folder URI on a mounted
            // volume - e.g. LocalSend's "open folder", which hands over a
            // com.android.externalstorage.documents tree/document URI - resolve it to the real local
            // path so we can actually browse it. Restricted to existing directories, so opening a
            // document/file keeps its current content-resolver behavior.
            externalStorageDirectoryPathOrNull()
                ?: run {
                    val uri = URI::class.createOrLog(toString())
                        // Some people use Uri.parse() without encoding their path. Let's try saving
                        // them by calling the other URI constructor that encodes everything.
                        ?: URI::class.createOrLog(scheme, userInfo, host, port, path, query, fragment)
                    uri?.let { Paths.get(it) }
                }
        }
        else -> null
    }

// Resolves an ExternalStorageProvider (com.android.externalstorage.documents) tree/document/bare
// URI that points to an existing directory on a mounted storage volume to the corresponding local
// Path, or null otherwise. Lets Material Files browse a folder another app opens via a SAF URI
// (which ContentPath can't list) by falling back to direct local access.
private fun Uri.externalStorageDirectoryPathOrNull(): Path? {
    if (authority != DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY) {
        return null
    }
    return runCatching {
        val documentId = when {
            DocumentsContractCompat.isDocumentUri(this) -> DocumentsContract.getDocumentId(this)
            DocumentsContractCompat.isTreeUri(this) -> DocumentsContract.getTreeDocumentId(this)
            else -> return@runCatching null
        }
        val colonIndex = documentId.indexOf(':')
        if (colonIndex < 0) {
            return@runCatching null
        }
        val rootId = documentId.substring(0, colonIndex)
        val relativePath = documentId.substring(colonIndex + 1)
        val directory = StorageVolumeListLiveData.valueCompat.firstNotNullOfOrNull { storageVolume ->
            val volumeRootId = if (storageVolume.isPrimaryCompat) {
                DocumentsContractCompat.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID
            } else {
                storageVolume.uuidCompat
            }
            if (volumeRootId == rootId) storageVolume.directoryCompat else null
        } ?: return@runCatching null
        val file = if (relativePath.isEmpty()) directory else File(directory, relativePath)
        if (!file.isDirectory) {
            return@runCatching null
        }
        Paths.get(file.path)
    }.getOrNull()
}

private const val EXTRA_PATH_URI_LIST = "${BuildConfig.APPLICATION_ID}.extra.PATH_URI_LIST"

var Intent.extraPathList: List<Path>
    // Generic: no Class<T> available at this call site for the API-33+ typed replacement.
    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    get() {
        val extraPathUris = (getSerializableExtra(EXTRA_PATH_URI_LIST) as List<URI>?)
            ?.takeIfNotEmpty()
        extraPathUris?.let { return it.map { uri -> Paths.get(uri) } }
        return listOfNotNull(extraPath)
    }
    set(value) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        val pathUris = value.map { it.toUri() }
        putExtra(EXTRA_PATH_URI_LIST, pathUris as Serializable)
    }

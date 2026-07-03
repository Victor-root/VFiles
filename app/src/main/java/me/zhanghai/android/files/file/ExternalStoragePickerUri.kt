/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import java8.nio.file.Path
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.compat.directoryCompat
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.uuidCompat
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.storage.StorageVolumeListLiveData
import me.zhanghai.android.files.util.valueCompat

// Support for the "systemPicker" build flavor: when Material Files replaces DocumentsUI as the
// platform document picker, it must hand back the exact same
// `com.android.externalstorage.documents` URIs that DocumentsUI would, so that apps requiring
// ExternalStorageProvider URIs (e.g. the whole SimpleMobileTools/Fossify family, which compares the
// returned tree URI for strict equality and then takePersistableUriPermission()s it) accept the
// result. The real file I/O is then served by the genuine ExternalStorageProvider, not by us.
//
// This whole path is inert in the standard build: it is gated on [canActAsExternalStoragePicker],
// which requires MANAGE_DOCUMENTS - a signature permission declared only by the systemPicker flavor
// manifest and only granted to a build signed with the platform key.

/**
 * Whether Material Files may hand back real ExternalStorageProvider URIs, i.e. it actually holds
 * [Manifest.permission.MANAGE_DOCUMENTS].
 *
 * This MUST gate every [externalStorageTreeUriOrNull] / [externalStorageDocumentUriOrNull] use: the
 * implicit activity-result grant of such a URI is not wrapped in a try/catch and the framework
 * throws when the granting app does not hold the permission. It is always false in the standard
 * build (the permission is not declared there) and true only in a systemPicker build signed with
 * the platform key.
 */
fun Context.canActAsExternalStoragePicker(): Boolean =
    checkSelfPermission(Manifest.permission.MANAGE_DOCUMENTS) == PackageManager.PERMISSION_GRANTED

/**
 * The `com.android.externalstorage.documents` **tree** URI DocumentsUI would return for [this] local
 * folder from `ACTION_OPEN_DOCUMENT_TREE`, or null when the folder is not served by
 * ExternalStorageProvider (remote backends, an unmounted volume, `Android/data`/`Android/obb`, or a
 * path outside any storage volume) - the caller must then fall back to [treeDocumentUri].
 */
fun Path.externalStorageTreeUriOrNull(): Uri? {
    val documentId = externalStorageDocumentIdOrNull() ?: return null
    return DocumentsContract.buildTreeDocumentUri(
        DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY, documentId
    )
}

/**
 * The `com.android.externalstorage.documents` **document** URI DocumentsUI would return for [this]
 * local file from `ACTION_OPEN_DOCUMENT`/`GET_CONTENT`, or null when the file is not served by
 * ExternalStorageProvider - the caller must then fall back to [fileProviderUri].
 */
fun Path.externalStorageDocumentUriOrNull(): Uri? {
    val documentId = externalStorageDocumentIdOrNull() ?: return null
    return DocumentsContract.buildDocumentUri(
        DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY, documentId
    )
}

// The ExternalStorageProvider document id "<rootId>:<relativePath>" for [this] path when it is a
// local file on a mounted storage volume, else null. rootId is "primary" for the primary volume and
// the volume UUID otherwise; the volume root is "primary:" with a trailing colon (what ESP's
// getRootFromDocId expects). Mirrors the volume matching in LinuxPath.isRootRequired and excludes
// Android/data|obb, which ExternalStorageProvider no longer serves since Android 11. Any failure
// (e.g. a StorageVolume API missing on an old device) falls back to null, i.e. our own provider.
private fun Path.externalStorageDocumentIdOrNull(): String? {
    if (!isLinuxPath) {
        return null
    }
    return runCatching {
        val file = toFile()
        for (storageVolume in StorageVolumeListLiveData.valueCompat) {
            val directory = storageVolume.directoryCompat ?: continue
            if (!file.startsWith(directory)) {
                continue
            }
            val relativePath = file.toRelativeString(directory)
            if (relativePath == ANDROID_DATA || relativePath.startsWith("$ANDROID_DATA/") ||
                relativePath == ANDROID_OBB || relativePath.startsWith("$ANDROID_OBB/")) {
                return@runCatching null
            }
            val rootId = if (storageVolume.isPrimaryCompat) {
                DocumentsContractCompat.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID
            } else {
                storageVolume.uuidCompat ?: continue
            }
            return@runCatching "$rootId:$relativePath"
        }
        null
    }.getOrNull()
}

private const val ANDROID_DATA = "Android/data"
private const val ANDROID_OBB = "Android/obb"

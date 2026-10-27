/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.ui.ConfirmationDialog
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser

fun Context.shareGalleryMedia(media: List<GalleryMedia>) {
    val intent = media.map { it.path.fileProviderUri }
        .createSendStreamIntent(media.map { it.mimeType })
        .withChooser()
    startActivitySafe(intent)
}

fun Context.openGalleryMediaWith(media: GalleryMedia) {
    val intent = media.path.fileProviderUri.createViewIntent(media.mimeType).withChooser()
    startActivitySafe(intent)
}

fun Context.showGalleryMediaInFolder(media: GalleryMedia) {
    startActivitySafe(FileListActivity.createViewIntent(media.path.parent))
}

/**
 * Moves photos and videos to the trash of the system, where they can be restored for a while,
 * after a confirmation by the system. Before Android 11, which has no trash, they are deleted
 * after a confirmation instead. [onDeleted] is called once they're gone.
 */
@Composable
fun rememberGalleryMediaDeleter(onDeleted: () -> Unit): (List<GalleryMedia>) -> Unit {
    val context = LocalContext.current
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            currentOnDeleted()
        }
    }
    var mediaToDelete by remember { mutableStateOf<List<GalleryMedia>?>(null) }
    mediaToDelete?.let { media ->
        ConfirmationDialog(
            text = pluralStringResource(
                R.plurals.gallery_delete_message_format, media.size, media.size
            ),
            onConfirm = {
                FileJobService.delete(media.map { it.path }, context)
                currentOnDeleted()
            },
            onDismissRequest = { mediaToDelete = null }
        )
    }
    return { media ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createTrashRequest(
                context.contentResolver, media.map { it.uri }, true
            )
            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            mediaToDelete = media
        }
    }
}

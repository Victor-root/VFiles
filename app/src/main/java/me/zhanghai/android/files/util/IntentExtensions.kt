/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.core.app.ShareCompat
import androidx.core.net.toUri
import me.zhanghai.android.files.app.appClassLoader
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.intentType
import me.zhanghai.android.files.viewer.saveas.SaveAsActivity
import kotlin.reflect.KClass

fun <T : Context> KClass<T>.createIntent(): Intent = Intent(application, java)

// @see com.android.documentsui.inspector.InspectorController.createGeoIntent
// @see https://developer.android.com/guide/components/intents-common.html#Maps
fun KClass<Intent>.createViewLocation(latitude: Float, longitude: Float, label: String): Intent =
    "geo:0,0?q=$latitude,$longitude(${Uri.encode(label)})".toUri().createViewIntent()

// Generic: no Class<T> available at these call sites for the API-33+ typed replacements.
@Suppress("DEPRECATION")
fun <T : Parcelable> Intent.getParcelableExtraSafe(key: String?): T? {
    setExtrasClassLoader(appClassLoader)
    return getParcelableExtra(key)
}

// Saving a file as another one is only of use for those of other apps, so it isn't offered for
// those of this app.
fun Intent.withChooser(title: CharSequence? = null, vararg initialIntents: Intent): Intent =
    Intent.createChooser(this, title).apply {
        putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents)
        putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(application, SaveAsActivity::class.java))
        )
    }

fun Intent.withChooser(vararg initialIntents: Intent) = withChooser(null, *initialIntents)

fun Uri.createEditIntent(mimeType: MimeType): Intent =
    Intent(Intent.ACTION_EDIT)
        // Calling setType() will clear data.
        .setDataAndType(this, mimeType.intentType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

fun MimeType.createPickFileIntent(allowMultiple: Boolean = false) =
    Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(value)
        .apply {
            if (allowMultiple) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

fun Collection<MimeType>.createPickFileIntent(allowMultiple: Boolean = false): Intent =
    (singleOrNull() ?: MimeType.ANY).createPickFileIntent(allowMultiple)
        .apply {
            if (size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, map { it.value }.toTypedArray())
            }
        }

fun Uri.createSendImageIntent(text: CharSequence? = null): Intent =
    createSendStreamIntent(MimeType.IMAGE_ANY).apply {
        text?.let {
            // For maximum compatibility.
            putExtra(Intent.EXTRA_TEXT, it)
            putExtra(Intent.EXTRA_TITLE, it)
            putExtra(Intent.EXTRA_SUBJECT, it)
            // HACK: WeChat moments respects this extra only.
            putExtra("Kdescription", it)
        }
    }

fun Uri.createSendStreamIntent(mimeType: MimeType): Intent =
    listOf(this).createSendStreamIntent(listOf(mimeType))

fun Collection<Uri>.createSendStreamIntent(mimeTypes: Collection<MimeType>): Intent =
    // Use ShareCompat.IntentBuilder for its migrateExtraStreamToClipData() because
    // Intent.migrateExtraStreamToClipData() won't promote child ClipData and flags to the chooser
    // intent, breaking third party share sheets.
    // The context parameter here is only used for passing calling activity information and starting
    // chooser activity, neither of which we care about.
    ShareCompat.IntentBuilder(application)
        .setType(mimeTypes.intentType)
        .apply { forEach { addStream(it) } }
        .intent
        // FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET is unnecessarily added by ShareCompat.IntentBuilder.
        .apply {
            @Suppress("DEPRECATION")
            removeFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
        }

// The files app of the system can read any directory, unlike this app which can't grant it the
// permission for directories like Android/data and would crash trying to.
fun Uri.createDocumentsUiViewDirectoryIntent(): Intent =
    createViewIntent(MimeType.DIRECTORY).apply {
        removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        DocumentsContractCompat.getDocumentsUiPackage()?.let { setPackage(it) }
    }

fun Uri.createViewIntent(): Intent = Intent(Intent.ACTION_VIEW, this)

fun Uri.createViewIntent(mimeType: MimeType): Intent =
    Intent(Intent.ACTION_VIEW)
        // Calling setType() will clear data.
        .setDataAndType(this, mimeType.intentType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

@Suppress("DEPRECATION")
fun Uri.createInstallPackageIntent(): Intent =
    Intent(Intent.ACTION_INSTALL_PACKAGE)
        .setDataAndType(this, MimeType.APK.value)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

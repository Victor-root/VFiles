/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.extension
import me.zhanghai.android.files.onboarding.OnboardingActivity
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.isExternalStorageAccessGranted
import me.zhanghai.android.files.util.takeIfNotEmpty
import me.zhanghai.android.files.util.valueCompat

class FileListActivity : ComposeActivity() {
    private val viewModel by viewModels<FileListViewModel>()

    private var isOnboarding = false

    private lateinit var controller: FileListController

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!Settings.ONBOARDING_COMPLETED.valueCompat) {
            if (Environment::class.isExternalStorageAccessGranted()) {
                // Existing install with permissions already granted: skip onboarding.
                Settings.ONBOARDING_COMPLETED.putValue(true)
            } else {
                isOnboarding = true
            }
        }
        // Sets the content, which is composed after this method.
        super.onCreate(savedInstanceState)

        if (isOnboarding) {
            startActivity(OnboardingActivity::class.createIntent())
            finish()
            return
        }
        if (!viewModel.hasTrail) {
            viewModel.initialize(intent)
        }
        controller = FileListController(this, viewModel, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (::controller.isInitialized) {
            controller.saveState(outState)
        }
    }

    @Composable
    override fun Content() {
        if (isOnboarding) {
            return
        }
        FileListScreen(controller, viewModel)
    }

    // Opens the folder and picking options asked for by the intent.
    private fun FileListViewModel.initialize(intent: Intent) {
        var path = intent.extraPath
        var pickOptions: PickOptions? = null
        when (val action = intent.action) {
            Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_CREATE_DOCUMENT -> {
                val mode = if (action == Intent.ACTION_CREATE_DOCUMENT) {
                    PickOptions.Mode.CREATE_FILE
                } else {
                    PickOptions.Mode.OPEN_FILE
                }
                val mimeType = intent.type?.asMimeTypeOrNull() ?: MimeType.ANY
                val fileName = if (mode == PickOptions.Mode.CREATE_FILE) {
                    intent.getStringExtra(Intent.EXTRA_TITLE)?.asFileNameOrNull()?.value
                        ?: mimeType.extension?.let { "file.$it" } ?: "file"
                } else {
                    null
                }
                val readOnly = action == Intent.ACTION_GET_CONTENT
                val extraMimeTypes = if (mode == PickOptions.Mode.OPEN_FILE) {
                    intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
                        ?.mapNotNull { it.asMimeTypeOrNull() }?.takeIfNotEmpty()
                } else {
                    null
                }
                val mimeTypes = extraMimeTypes ?: listOf(mimeType)
                val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
                val allowMultiple = mode != PickOptions.Mode.CREATE_FILE &&
                    intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                pickOptions =
                    PickOptions(mode, fileName, readOnly, mimeTypes, localOnly, allowMultiple)
            }
            Intent.ACTION_OPEN_DOCUMENT_TREE -> {
                val localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)
                pickOptions = PickOptions(
                    PickOptions.Mode.OPEN_DIRECTORY, null, false, emptyList(), localOnly, false
                )
            }
            ACTION_VIEW_DOWNLOADS ->
                path = Paths.get(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .path
                )
            else ->
                if (path != null) {
                    val mimeType = intent.type?.asMimeTypeOrNull()
                    if (mimeType != null && path.isArchiveFile(mimeType)) {
                        path = path.createArchiveRootPath()
                    }
                }
        }
        resetTo(path ?: Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat)
        if (pickOptions != null) {
            this.pickOptions = pickOptions
        }
    }

    companion object {
        private const val ACTION_VIEW_DOWNLOADS =
            "me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS"

        fun createViewIntent(path: Path): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_VIEW)
                .apply { extraPath = path }
    }

    class OpenFileContract : ActivityResultContract<List<MimeType>, Path?>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .setType(MimeType.ANY.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, input.map { it.value }.toTypedArray())

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class CreateFileContract : ActivityResultContract<Triple<MimeType, String?, Path?>, Path?>() {
        override fun createIntent(
            context: Context,
            input: Triple<MimeType, String?, Path?>
        ): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_CREATE_DOCUMENT)
                .setType(input.first.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .apply {
                    input.second?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    input.third?.let { extraPath = it }
                }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class OpenDirectoryContract : ActivityResultContract<Path?, Path?>() {
        override fun createIntent(context: Context, input: Path?): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .apply { input?.let { extraPath = it } }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }
}

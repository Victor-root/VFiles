/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.os.BundleCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.app.AppActivity
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

class FileListActivity : AppActivity() {
    private val viewModel by viewModels<FileListViewModel>()

    private var isOnboarding = false

    private lateinit var controller: FileListController

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!Settings.ONBOARDING_COMPLETED.value) {
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
            viewModel.initialize(intent, savedInstanceState?.currentPath)
            savedInstanceState?.restorePasteState()
        }
        controller = FileListController(this, viewModel, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (::controller.isInitialized) {
            controller.saveState(outState)
            outState.putParcelable(KEY_CURRENT_PATH, viewModel.currentPath as Parcelable)
            savePasteState(outState)
        }
    }

    // Files can be picked for another app from the screens opened above the file list too.
    @Composable
    override fun ProvideScreenLocals(content: @Composable () -> Unit) {
        if (isOnboarding) {
            content()
            return
        }
        val pickOptions by viewModel.pickOptionsFlow.collectAsStateWithLifecycle()
        val filePicker = remember(pickOptions) {
            pickOptions?.takeIf { it.mode == PickOptions.Mode.OPEN_FILE }?.let { options ->
                FilePicker(
                    accepts = { mimeType -> options.mimeTypes.any { it.match(mimeType) } },
                    allowsMultiple = options.allowMultiple,
                    pick = controller::pickFiles
                )
            }
        }
        CompositionLocalProvider(LocalFilePicker provides filePicker, content = content)
    }

    @Composable
    override fun Content() {
        if (isOnboarding) {
            return
        }
        FileListScreen(controller, viewModel)
    }

    // Opens the folder and picking options asked for by the intent, or the folder that was open
    // before the process died.
    private fun FileListViewModel.initialize(intent: Intent, restoredPath: Path?) {
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
        resetTo(restoredPath ?: path ?: Settings.FILE_LIST_DEFAULT_DIRECTORY.value)
        if (pickOptions != null) {
            this.pickOptions = pickOptions
        }
    }

    // Only so many, since all of the saved state has to fit in a transaction with the system.
    private fun savePasteState(outState: Bundle) {
        val pasteState = viewModel.pasteState
        if (pasteState.files.size in 1..PASTE_FILES_SAVED_MAX) {
            outState.putBoolean(KEY_PASTE_COPY, pasteState.copy)
            outState.putParcelable(KEY_PASTE_FILES, pasteState.files)
        }
    }

    private fun Bundle.restorePasteState() {
        val files = BundleCompat.getParcelable(this, KEY_PASTE_FILES, FileItemSet::class.java)
        if (files != null) {
            viewModel.addToPasteState(getBoolean(KEY_PASTE_COPY), files)
        }
    }

    private val Bundle.currentPath: Path?
        get() = BundleCompat.getParcelable(this, KEY_CURRENT_PATH, Parcelable::class.java) as Path?

    companion object {
        private const val KEY_CURRENT_PATH = "current_path"
        private const val KEY_PASTE_COPY = "paste_copy"
        private const val KEY_PASTE_FILES = "paste_files"

        private const val PASTE_FILES_SAVED_MAX = 100

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

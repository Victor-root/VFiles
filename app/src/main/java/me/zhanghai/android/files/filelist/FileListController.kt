/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.BundleCompat
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.checkSelfPermissionCompat
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.canActAsExternalStoragePicker
import me.zhanghai.android.files.file.externalStorageDocumentUriOrNull
import me.zhanghai.android.files.file.externalStorageTreeUriOrNull
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.treeDocumentUri
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.navigation.BookmarkDirectories
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.settings.FileOpenAppCategory
import me.zhanghai.android.files.settings.FileOpenDefaultApps
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.terminal.Terminal
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.create
import me.zhanghai.android.files.util.createInstallPackageIntent
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.supportsExternalStorageManager
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.files.viewer.image.ImageViewerActivity

/**
 * What the file list does, for the lifetime of its activity: opening, picking and acting on files,
 * the dialogs it shows, and the storage access it asks for.
 */
class FileListController(
    private val activity: ComponentActivity,
    private val viewModel: FileListViewModel,
    savedInstanceState: Bundle?
) : FileItemActions {
    private val requestAllFilesAccessLauncher = activity.registerForActivityResult(
        RequestAllFilesAccessContract(), ::onRequestAllFilesAccessResult
    )
    private val requestStoragePermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onRequestStoragePermissionResult
    )
    private val requestStoragePermissionInSettingsLauncher = activity.registerForActivityResult(
        RequestPermissionInSettingsContract(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        ::onRequestStoragePermissionInSettingsResult
    )

    val gridState: LazyGridState

    var dialog: FileListDialog? by mutableStateOf(null)

    /** The name of the file to create when picking where to create a file. */
    val createFileNameState: TextFieldState

    init {
        val state = savedInstanceState?.let {
            BundleCompat.getParcelable(it, KEY_STATE, State::class.java)
        }
        gridState = createGridState(state?.scrollIndex ?: 0, state?.scrollOffset ?: 0)
        dialog = state?.dialog
        // Only used when picking where to create a file, which comes with a name.
        val createFileName = state?.createFileName ?: viewModel.pickOptions?.fileName ?: ""
        createFileNameState = TextFieldState(
            createFileName,
            TextRange(0, createFileName.asFileNameOrNull()?.baseName?.length ?: 0)
        )
    }

    fun saveState(outState: Bundle) {
        outState.putParcelable(
            KEY_STATE,
            State(
                gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, dialog,
                createFileNameState.text.toString()
            )
        )
    }

    private val currentPath: Path
        get() = viewModel.currentPath

    private val displayedFiles: List<FileItem>
        get() = viewModel.displayedFiles

    fun collapseSearch() {
        viewModel.isSearchViewExpanded = false
        viewModel.stopSearching()
    }

    fun navigateTo(path: Path) {
        collapseSearch()
        val state = FileListScrollState(
            gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset
        )
        viewModel.navigateTo(state, path)
    }

    fun navigateToRoot(path: Path) {
        collapseSearch()
        viewModel.resetTo(path)
    }

    fun navigateUp() {
        collapseSearch()
        viewModel.navigateUp()
    }

    /** Scrolls back to where the list was when the current folder was left, if it was. */
    suspend fun restorePendingScrollState() {
        val state = viewModel.pendingState as? FileListScrollState ?: return
        gridState.scrollToItem(state.index, state.offset)
    }

    fun refresh() {
        viewModel.reload()
    }

    fun newTask() {
        openInNewTask(currentPath)
    }

    fun openInNewTask(path: Path) {
        val intent = FileListActivity.createViewIntent(path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        activity.startActivitySafe(intent)
    }

    fun copyPath(path: Path) {
        clipboardManager.copyText(path.toUserFriendlyString(), activity)
    }

    fun toggleShowHiddenFiles() {
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(
            !Settings.FILE_LIST_SHOW_HIDDEN_FILES.value
        )
    }

    fun shareCurrentDirectory() {
        shareFiles(listOf(currentPath), listOf(MimeType.DIRECTORY))
    }

    fun copyCurrentPath() {
        copyPath(currentPath)
    }

    val canOpenInTerminal: Boolean
        get() = currentPath.isLinuxPath

    fun openInTerminal() {
        Terminal.open(currentPath.toFile().path, activity)
    }

    fun addCurrentDirectoryBookmark() {
        addBookmark(currentPath)
    }

    fun createCurrentDirectoryShortcut() {
        createShortcut(currentPath, MimeType.DIRECTORY)
    }

    fun showNavigateToPathDialog() {
        dialog = FileListDialog.NavigateToPath(currentPath)
    }

    fun showCreateFileDialog() {
        dialog = FileListDialog.CreateFile
    }

    fun showCreateDirectoryDialog() {
        dialog = FileListDialog.CreateDirectory
    }

    fun isFileSelectable(file: FileItem): Boolean {
        val pickOptions = viewModel.pickOptions ?: return true
        return when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
                !file.attributes.isDirectory &&
                    pickOptions.mimeTypes.any { it.match(file.mimeType) }
            PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
        }
    }

    // A click opens the file, unless files are being selected.
    fun onFileClick(file: FileItem) {
        if (viewModel.selectedFiles.isEmpty()) {
            openFile(file)
        } else {
            toggleFileSelection(file)
        }
    }

    // A long click starts selecting files, or opens the file while they're being selected.
    fun onFileLongClick(file: FileItem) {
        if (viewModel.selectedFiles.isEmpty()) {
            toggleFileSelection(file)
        } else {
            openFile(file)
        }
    }

    fun toggleFileSelection(file: FileItem) {
        if (!isFileSelectable(file)) {
            return
        }
        val selected = file in viewModel.selectedFiles
        val pickOptions = viewModel.pickOptions
        if (!selected && pickOptions != null && !pickOptions.allowMultiple) {
            viewModel.clearSelectedFiles()
        }
        viewModel.selectFile(file, !selected)
    }

    val canSelectAll: Boolean
        get() {
            val pickOptions = viewModel.pickOptions
            return pickOptions == null || pickOptions.allowMultiple
        }

    fun selectAllFiles() {
        val files = displayedFiles.filterTo(fileItemSetOf()) { isFileSelectable(it) }
        viewModel.selectFiles(files, true)
    }

    fun clearSelectedFiles() {
        viewModel.clearSelectedFiles()
    }

    private fun openFile(file: FileItem) {
        val pickOptions = viewModel.pickOptions
        if (pickOptions != null) {
            if (file.attributes.isDirectory) {
                navigateTo(file.path)
            } else {
                when (pickOptions.mode) {
                    PickOptions.Mode.OPEN_FILE -> pickPaths(linkedSetOf(file.path))
                    PickOptions.Mode.CREATE_FILE -> confirmReplaceFile(file, true)
                    PickOptions.Mode.OPEN_DIRECTORY -> {}
                }
            }
            return
        }
        if (file.mimeType.isApk) {
            openApk(file)
            return
        }
        if (file.isListable) {
            navigateTo(file.listablePath)
            return
        }
        openFileWithIntent(file, false)
    }

    private fun openApk(file: FileItem) {
        if (!file.isListable) {
            installApk(file)
            return
        }
        when (Settings.OPEN_APK_DEFAULT_ACTION.value) {
            OpenApkDefaultAction.INSTALL -> installApk(file)
            OpenApkDefaultAction.VIEW -> viewApk(file)
            OpenApkDefaultAction.ASK -> dialog = FileListDialog.OpenApk(file)
        }
    }

    fun installApk(file: FileItem) {
        val path = file.path
        if (!path.isArchivePath) {
            activity.startActivitySafe(path.fileProviderUri.createInstallPackageIntent())
        } else {
            FileJobService.installApk(path, activity)
        }
    }

    fun viewApk(file: FileItem) {
        navigateTo(file.listablePath)
    }

    override fun openFileWith(file: FileItem) {
        openFileWithIntent(file, true)
    }

    private fun openFileWithIntent(file: FileItem, withChooser: Boolean) {
        val path = file.path
        val mimeType = file.mimeType
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, withChooser, activity)
            return
        }
        val intent = path.fileProviderUri.createViewIntent(mimeType)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply {
                extraPath = path
                maybeAddImageViewerActivityExtras(this, path, mimeType)
                if (!withChooser) {
                    applyDefaultAppPackage(this, mimeType)
                }
            }
            .let {
                if (withChooser) {
                    it.withChooser(
                        EditFileActivity::class.createIntent()
                            .putArgs(EditFileActivity.Args(path, mimeType)),
                        OpenFileAsDialogActivity::class.createIntent()
                            .putArgs(OpenFileAsDialogActivity.Args(path))
                    )
                } else {
                    it
                }
            }
        activity.startActivitySafe(intent)
    }

    // If the user picked a default app for this file's category (Settings > Default apps), open it
    // directly with that app instead of asking, or else with the viewer of this app when there's
    // one, but only when the app can actually handle the file, otherwise fall back to the normal
    // handling.
    private fun applyDefaultAppPackage(intent: Intent, mimeType: MimeType) {
        val category = FileOpenAppCategory.forMimeType(mimeType) ?: return
        val packageName = FileOpenDefaultApps.getPackage(category)
            ?: activity.packageName.takeIf { category.builtInViewerTitleRes != null }
            ?: return
        val canHandle = Intent(intent).setPackage(packageName)
            .resolveActivity(activity.packageManager) != null
        if (canHandle) {
            intent.setPackage(packageName)
        }
    }

    // The image viewer can go through the other images of the folder, in the order shown.
    private fun maybeAddImageViewerActivityExtras(intent: Intent, path: Path, mimeType: MimeType) {
        if (!mimeType.isImage) {
            return
        }
        var paths = displayedFiles.mapNotNull { file ->
            file.path.takeIf { file.mimeType.isImage || it == path }
        }
        var position = paths.indexOf(path)
        if (position == -1) {
            return
        }
        // Don't send too many paths to avoid TransactionTooLargeException.
        if (paths.size > IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX) {
            val start = (position - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX / 2)
                .coerceIn(0, paths.size - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            paths = paths.subList(start, start + IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            position -= start
        }
        ImageViewerActivity.putExtras(intent, paths, position)
    }

    override fun cutFile(file: FileItem) {
        cutFiles(fileItemSetOf(file))
    }

    fun cutFiles(files: FileItemSet) {
        viewModel.addToPasteState(false, files)
        viewModel.selectFiles(files, false)
    }

    override fun copyFile(file: FileItem) {
        copyFiles(fileItemSetOf(file))
    }

    fun copyFiles(files: FileItemSet) {
        viewModel.addToPasteState(true, files)
        viewModel.selectFiles(files, false)
    }

    override fun confirmDeleteFile(file: FileItem) {
        confirmDeleteFiles(fileItemSetOf(file))
    }

    fun confirmDeleteFiles(files: FileItemSet) {
        dialog = FileListDialog.ConfirmDeleteFiles(files)
    }

    fun deleteFiles(files: FileItemSet) {
        FileJobService.delete(makePathListForJob(files), activity)
        viewModel.selectFiles(files, false)
    }

    override fun showRenameFileDialog(file: FileItem) {
        dialog = FileListDialog.RenameFile(file)
    }

    fun renameFile(file: FileItem, newName: String) {
        FileJobService.rename(file.path, newName, activity)
        viewModel.selectFile(file, false)
    }

    override fun extractFile(file: FileItem) {
        copyFile(file.createDummyArchiveRoot())
    }

    fun extractFiles(files: FileItemSet) {
        copyFiles(files.mapTo(fileItemSetOf()) { it.createDummyArchiveRoot() })
        viewModel.selectFiles(files, false)
    }

    override fun showCreateArchiveDialog(file: FileItem) {
        showCreateArchiveDialog(fileItemSetOf(file))
    }

    fun showCreateArchiveDialog(files: FileItemSet) {
        dialog = FileListDialog.CreateArchive(files)
    }

    fun archive(files: FileItemSet, name: String, type: ArchiveType, password: String?) {
        val archiveFile = currentPath.resolve(name)
        FileJobService.archive(
            makePathListForJob(files), archiveFile, type.format, type.filter, password, activity
        )
        viewModel.selectFiles(files, false)
    }

    override fun shareFile(file: FileItem) {
        shareFiles(listOf(file.path), listOf(file.mimeType))
    }

    fun shareFiles(files: FileItemSet) {
        shareFiles(files.map { it.path }, files.map { it.mimeType })
        viewModel.selectFiles(files, false)
    }

    private fun shareFiles(paths: List<Path>, mimeTypes: List<MimeType>) {
        val intent = paths.map { it.fileProviderUri }.createSendStreamIntent(mimeTypes)
            .withChooser()
        activity.startActivitySafe(intent)
    }

    override fun copyPath(file: FileItem) {
        copyPath(file.path)
    }

    override fun addBookmark(file: FileItem) {
        addBookmark(file.path)
    }

    private fun addBookmark(path: Path) {
        BookmarkDirectories.add(BookmarkDirectory(null, path))
        activity.showToast(R.string.file_add_bookmark_success)
    }

    override fun createShortcut(file: FileItem) {
        createShortcut(file.path, file.mimeType)
    }

    private fun createShortcut(path: Path, mimeType: MimeType) {
        val isDirectory = mimeType == MimeType.DIRECTORY
        val shortcutInfo = ShortcutInfoCompat.Builder(activity, path.toString())
            .setShortLabel(path.name)
            .setIntent(
                if (isDirectory) {
                    FileListActivity.createViewIntent(path)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                } else {
                    OpenFileActivity.createIntent(path, mimeType)
                }
            )
            .setIcon(
                IconCompat.createWithResource(
                    activity,
                    if (isDirectory) {
                        R.mipmap.directory_shortcut_icon
                    } else {
                        R.mipmap.file_shortcut_icon
                    }
                )
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(activity, shortcutInfo, null)
    }

    override fun showPropertiesDialog(file: FileItem) {
        dialog = FileListDialog.FileProperties(file)
    }

    fun hasFileWithName(name: String): Boolean = getFileWithName(name) != null

    private fun getFileWithName(name: String): FileItem? {
        val fileListData = viewModel.fileListStateful
        if (fileListData !is Success) {
            return null
        }
        return fileListData.value.find { it.name == name }
    }

    fun createFile(name: String) {
        FileJobService.create(currentPath.resolve(name), false, activity)
    }

    fun createDirectory(name: String) {
        FileJobService.create(currentPath.resolve(name), true, activity)
    }

    fun pasteFiles() {
        val pasteState = viewModel.pasteState
        val paths = makePathListForJob(pasteState.files)
        if (pasteState.copy) {
            FileJobService.copy(paths, currentPath, activity)
        } else {
            FileJobService.move(paths, currentPath, activity)
        }
        viewModel.clearPasteState()
    }

    private fun makePathListForJob(files: FileItemSet): List<Path> =
        files.map { it.path }.sortedBy { it.toUri() }

    fun pickSelectedFiles() {
        pickPaths(viewModel.selectedFiles.mapTo(linkedSetOf()) { it.path })
    }

    fun pickCurrentDirectory() {
        pickPaths(linkedSetOf(currentPath))
    }

    // The selected file is replaced by the created one.
    fun createSelectedFile() {
        confirmReplaceFile(viewModel.selectedFiles.single(), true)
    }

    fun createFileWithEnteredName() {
        val fileName = createFileNameState.text.toString()
        when {
            fileName.isEmpty() ->
                activity.showToast(R.string.file_list_create_file_name_error_empty)
            fileName.asFileNameOrNull() == null ->
                activity.showToast(R.string.file_list_create_file_name_error_invalid)
            else -> {
                val file = getFileWithName(fileName)
                if (file != null) {
                    confirmReplaceFile(file, false)
                } else {
                    pickPaths(linkedSetOf(currentPath.resolve(fileName)))
                }
            }
        }
    }

    private fun confirmReplaceFile(file: FileItem, setFileName: Boolean) {
        if (setFileName) {
            val fileName = file.name
            createFileNameState.edit {
                replace(0, length, fileName)
                selection = TextRange(0, fileName.asFileName().baseName.length)
            }
        }
        dialog = FileListDialog.ConfirmReplaceFile(file)
    }

    fun replaceFile(file: FileItem) {
        pickPaths(linkedSetOf(file.path))
    }

    fun cancelPicking() {
        activity.finish()
    }

    private fun pickPaths(paths: LinkedHashSet<Path>) {
        val pickOptions = viewModel.pickOptions!!
        // Resolve each selected path to the URI handed back to the caller. Files are shared through
        // the (private) FileProvider - the long-proven path that also works for SMB/FTP/archives;
        // only ACTION_OPEN_DOCUMENT_TREE needs a SAF tree URI, which must come from
        // FileSystemDocumentsProvider.
        val singlePath = paths.singleOrNull()
        // When Material Files is the platform document picker (systemPicker builds signed with the
        // platform key, holding MANAGE_DOCUMENTS), hand back the same
        // com.android.externalstorage.documents URIs that DocumentsUI would, so apps that require
        // ExternalStorageProvider URIs accept the result and the real I/O goes through the genuine
        // ExternalStorageProvider. Everything this doesn't cover - the whole standard build (the
        // permission is never held there), remote backends, unmounted volumes, Android/data|obb,
        // and CREATE_FILE (ESP only serves an existing document; the file is created lazily on
        // first write through our FileProvider) - falls back to our own provider URIs, unchanged.
        // See ExternalStoragePickerUri.kt.
        val canUseExternalStorage = activity.canActAsExternalStoragePicker()
        fun pickUriForPath(path: Path): Uri {
            if (canUseExternalStorage) {
                when (pickOptions.mode) {
                    PickOptions.Mode.OPEN_DIRECTORY ->
                        path.externalStorageTreeUriOrNull()?.let { return it }
                    PickOptions.Mode.OPEN_FILE ->
                        path.externalStorageDocumentUriOrNull()?.let { return it }
                    PickOptions.Mode.CREATE_FILE -> {}
                }
            }
            return if (pickOptions.mode == PickOptions.Mode.OPEN_DIRECTORY) {
                path.treeDocumentUri
            } else {
                path.fileProviderUri
            }
        }
        val uris = paths.map { pickUriForPath(it) }
        var flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        if (!pickOptions.readOnly) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        if (pickOptions.mode == PickOptions.Mode.OPEN_DIRECTORY) {
            flags = flags or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        }
        val mimeTypes = pickOptions.mimeTypes.map { it.value }
        val intent = Intent().apply {
            if (singlePath != null) {
                data = uris.single()
                extraPath = singlePath
                // Also carry the grant through ClipData, the channel some callers read instead of
                // the intent flags.
                if (mimeTypes.isNotEmpty()) {
                    clipData =
                        ClipData::class.create(null, mimeTypes, listOf(ClipData.Item(data!!)))
                }
            } else {
                clipData = ClipData::class.create(null, mimeTypes, uris.map { ClipData.Item(it) })
                extraPathList = paths.toList()
            }
            addFlags(flags)
        }
        // Belt-and-suspenders: besides the grant flags on the result intent, grant the URI(s) to
        // the calling package explicitly. The implicit activity-result grant is tied to the
        // lifetime of the result-receiving activity, so a caller that reads the URI later or from
        // another component can hit "requires the provider be exported, or grantUriPermission()".
        // An explicit grant is recorded in our own UriGrantsManager and stays valid until revoked
        // or reboot, regardless of the caller's lifecycle, while keeping the provider private
        // (exported=false).
        val callingPackage = activity.callingPackage
        if (callingPackage != null) {
            val grantFlags = flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
            for (uri in uris) {
                try {
                    activity.grantUriPermission(callingPackage, uri, grantFlags)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
        activity.setResult(Activity.RESULT_OK, intent)
        activity.finish()
    }

    /** The label of the app that opened this picker, or null if unknown, the system or us. */
    val callingAppLabel: String?
        get() {
            val callingPackage = activity.callingPackage ?: return null
            if (callingPackage == "android" || callingPackage == activity.packageName) {
                return null
            }
            return try {
                val packageManager = activity.packageManager
                packageManager.getApplicationInfo(callingPackage, 0).loadLabel(packageManager)
                    .toString()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

    /** Handles the keyboard shortcuts of the menus, like Ctrl+A to select all files. */
    fun onKeyShortcut(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
            return false
        }
        val selectedFiles = viewModel.selectedFiles
        val isPicking = viewModel.pickOptions != null
        val isAnyFileReadOnly = selectedFiles.any { it.path.fileSystem.isReadOnly }
        val canPaste = !isPicking && viewModel.pasteState.files.isNotEmpty() &&
            !currentPath.fileSystem.isReadOnly
        when {
            event.key == Key.A && canSelectAll -> selectAllFiles()
            selectedFiles.isNotEmpty() && !isPicking && event.key == Key.X && !isAnyFileReadOnly ->
                cutFiles(selectedFiles)
            selectedFiles.isNotEmpty() && !isPicking && event.key == Key.C ->
                copyFiles(selectedFiles)
            event.key == Key.V && canPaste -> pasteFiles()
            event.key == Key.L -> showNavigateToPathDialog()
            event.key == Key.R -> refresh()
            event.key == Key.H -> toggleShowHiddenFiles()
            else -> return false
        }
        return true
    }

    fun ensureStorageAccess() {
        if (viewModel.isStorageAccessRequested) {
            return
        }
        if (Environment::class.supportsExternalStorageManager()) {
            if (!Environment.isExternalStorageManager()) {
                dialog = FileListDialog.AllFilesAccessRationale
                viewModel.isStorageAccessRequested = true
            }
        } else if (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (activity.shouldShowRequestPermissionRationale(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )) {
                dialog = FileListDialog.StoragePermissionRationale
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            viewModel.isStorageAccessRequested = true
        }
    }

    fun onAllFilesAccessRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestAllFilesAccessLauncher.launch(Unit)
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun onRequestAllFilesAccessResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            refresh()
        }
    }

    fun onStoragePermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun onRequestStoragePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isStorageAccessRequested = false
            refresh()
        } else if (activity.shouldShowRequestPermissionRationale(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )) {
            dialog = FileListDialog.StoragePermissionRationale
        } else {
            dialog = FileListDialog.StoragePermissionInSettingsRationale
        }
    }

    fun onStoragePermissionInSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermissionInSettingsLauncher.launch(Unit)
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun onRequestStoragePermissionInSettingsResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            refresh()
        }
    }

    companion object {
        private val KEY_STATE = "${FileListController::class.java.name}.state"

        private const val IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX = 1000

        private val GRID_CACHE_WINDOW = 360.dp

        // Items are composed a bit ahead while scrolling, and kept a bit after leaving the
        // screen, so that fewer of them are composed in the frames where they appear.
        @OptIn(ExperimentalFoundationApi::class)
        private fun createGridState(scrollIndex: Int, scrollOffset: Int): LazyGridState =
            LazyGridState(
                LazyLayoutCacheWindow(ahead = GRID_CACHE_WINDOW, behind = GRID_CACHE_WINDOW),
                scrollIndex,
                scrollOffset
            )
    }

    @Parcelize
    private class State(
        val scrollIndex: Int,
        val scrollOffset: Int,
        val dialog: FileListDialog?,
        val createFileName: String
    ) : Parcelable

    private class RequestAllFilesAccessContract : ActivityResultContract<Unit, Boolean>() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun createIntent(context: Context, input: Unit): Intent =
            Environment::class.createManageAppAllFilesAccessPermissionIntent(context.packageName)

        @RequiresApi(Build.VERSION_CODES.R)
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            Environment.isExternalStorageManager()
    }

    private class RequestPermissionInSettingsContract(
        private val permissionName: String
    ) : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            application.checkSelfPermissionCompat(permissionName) ==
                PackageManager.PERMISSION_GRANTED
    }
}

/** Where the list was scrolled, to come back there when going back up to a folder. */
@Parcelize
class FileListScrollState(val index: Int, val offset: Int) : Parcelable

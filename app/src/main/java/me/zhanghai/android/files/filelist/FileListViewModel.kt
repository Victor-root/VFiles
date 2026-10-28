/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import me.zhanghai.android.files.navigation.NavigationRoot
import me.zhanghai.android.files.navigation.NavigationRootMap
import me.zhanghai.android.files.provider.archive.archiveRefresh
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.provider.common.search
import me.zhanghai.android.files.settings.PathSettings
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.StatefulLoader
import me.zhanghai.android.files.util.Success
import java.io.IOException

// TODO: Use SavedStateHandle to save state.
// The getters read the state they derive from rather than the flows derived from it, so that they
// are up to date right after a change.
@OptIn(ExperimentalCoroutinesApi::class)
class FileListViewModel : ViewModel() {
    private val trail = MutableStateFlow<TrailData?>(null)
    val hasTrail: Boolean
        get() = trail.value != null
    val pendingState: Parcelable?
        get() = trail.value!!.pendingState

    private fun setTrail(trailData: TrailData) {
        val path = trailData.currentPath
        if (trail.value?.currentPath != path) {
            _pathSettings.value = PathSettingsHolder(path)
        }
        trail.value = trailData
    }

    fun navigateTo(lastState: Parcelable, path: Path) =
        setTrail(trail.value?.navigateTo(lastState, path) ?: TrailData.of(path))

    fun resetTo(path: Path) = setTrail(TrailData.of(path))

    fun navigateUp(): Boolean {
        val trailData = trail.value?.navigateUp() ?: return false
        setTrail(trailData)
        return true
    }

    val currentPathFlow: StateFlow<Path?> =
        trail.map { it?.currentPath }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val currentPath: Path
        get() = trail.value!!.currentPath

    private val _searchStateFlow = MutableStateFlow(SearchState(false, ""))
    val searchStateFlow: StateFlow<SearchState>
        get() = _searchStateFlow
    val searchState: SearchState
        get() = _searchStateFlow.value

    fun search(query: String) {
        val searchState = searchState
        if (searchState.isSearching && searchState.query == query) {
            return
        }
        _searchStateFlow.value = SearchState(true, query)
    }

    fun stopSearching() {
        if (!searchState.isSearching) {
            return
        }
        _searchStateFlow.value = SearchState(false, "")
    }

    private val _fileListFlow = MutableStateFlow<Stateful<List<FileItem>>>(Loading(null))
    val fileListFlow: StateFlow<Stateful<List<FileItem>>>
        get() = _fileListFlow
    val fileListStateful: Stateful<List<FileItem>>
        get() = _fileListFlow.value

    // The loader of the directory shown when not searching, which can load it again.
    private var directoryLoader: StatefulLoader<List<FileItem>>? = null

    // Increased to search again.
    private val searchRequests = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // Each new trail lists its directory again, even if it's the same one.
            combine(trail.filterNotNull(), _searchStateFlow, searchRequests) { trail, search, _ ->
                trail.currentPath to search
            }
                .collectLatest { (path, searchState) ->
                    if (searchState.isSearching) {
                        searchFiles(path, searchState.query)
                    } else {
                        listFiles(path)
                    }
                }
        }
    }

    fun reload() {
        val path = currentPath
        if (path.isArchivePath) {
            path.archiveRefresh()
        }
        if (searchState.isSearching) {
            ++searchRequests.value
        } else {
            directoryLoader?.load()
        }
    }

    // Lists the files of the directory, and again whenever it changes.
    private suspend fun listFiles(path: Path): Unit = coroutineScope {
        val loader = StatefulLoader(this) { path.loadDirectoryFileItems() }
        directoryLoader = loader
        try {
            launch { loader.flow.collect { _fileListFlow.value = it } }
            loader.load()
            path.observeChanges().collect { loader.load() }
        } finally {
            if (directoryLoader === loader) {
                directoryLoader = null
            }
        }
    }

    private fun Path.loadDirectoryFileItems(): List<FileItem> =
        newDirectoryStream().use { directoryStream ->
            val fileList = mutableListOf<FileItem>()
            for (path in directoryStream) {
                try {
                    fileList.add(path.loadFileItem())
                } catch (e: DirectoryIteratorException) {
                    // TODO: Ignoring such a file can be misleading and we need to support files
                    //  without information.
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            fileList
        }

    // Searches the files under the directory, showing the ones found as they are.
    private suspend fun searchFiles(path: Path, query: String) {
        _fileListFlow.value = Loading(emptyList())
        val job = currentCoroutineContext().job
        val fileList = mutableListOf<FileItem>()
        _fileListFlow.value = try {
            runInterruptible(Dispatchers.IO) {
                path.search(query, SEARCH_INTERVAL_MILLIS) { paths: List<Path> ->
                    for (path in paths) {
                        val fileItem = try {
                            path.loadFileItem()
                        } catch (e: IOException) {
                            e.printStackTrace()
                            // TODO: Support file without information.
                            continue
                        }
                        fileList.add(fileItem)
                    }
                    // A cancelled search may still find files before it's interrupted.
                    if (job.isActive) {
                        _fileListFlow.value = Loading(fileList.toList())
                    }
                }
            }
            Success(fileList)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Failure(_fileListFlow.value.value, e)
        }
    }

    val searchViewExpandedFlow = MutableStateFlow(false)
    var isSearchViewExpanded: Boolean
        get() = searchViewExpandedFlow.value
        set(value) {
            searchViewExpandedFlow.value = value
        }

    var searchViewQuery = ""

    val breadcrumbFlow: StateFlow<BreadcrumbData?> =
        combine(trail.filterNotNull(), NavigationRootMap.flow, ::createBreadcrumbData)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // The settings of the current path, held here since the shared preferences only keep weak
    // references to the settings listening to them.
    private class PathSettingsHolder(path: Path) {
        val viewType = PathSettings.getFileListViewType(path)
        val sortOptions = PathSettings.getFileListSortOptions(path)
    }

    private val _pathSettings = MutableStateFlow<PathSettingsHolder?>(null)

    val viewTypeFlow: StateFlow<FileViewType> = _pathSettings.filterNotNull()
        .flatMapLatest { pathSettings ->
            combine(pathSettings.viewType.flow, Settings.FILE_LIST_VIEW_TYPE.flow) {
                pathViewType, viewType -> pathViewType ?: viewType
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings.FILE_LIST_VIEW_TYPE.value)
    var viewType: FileViewType
        get() = _pathSettings.value?.viewType?.value ?: Settings.FILE_LIST_VIEW_TYPE.value
        set(value) {
            val pathViewType = _pathSettings.value!!.viewType
            if (pathViewType.value != null) {
                pathViewType.putValue(value)
            } else {
                Settings.FILE_LIST_VIEW_TYPE.putValue(value)
            }
        }

    val sortOptionsFlow: StateFlow<FileSortOptions> = _pathSettings.filterNotNull()
        .flatMapLatest { pathSettings ->
            combine(pathSettings.sortOptions.flow, Settings.FILE_LIST_SORT_OPTIONS.flow) {
                pathSortOptions, sortOptions -> pathSortOptions ?: sortOptions
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings.FILE_LIST_SORT_OPTIONS.value)
    private val sortOptions: FileSortOptions
        get() = _pathSettings.value?.sortOptions?.value ?: Settings.FILE_LIST_SORT_OPTIONS.value

    fun setSortBy(by: By) = setSortOptions(sortOptions.copy(by = by))

    fun setSortOrder(order: Order) = setSortOptions(sortOptions.copy(order = order))

    fun setSortDirectoriesFirst(isDirectoriesFirst: Boolean) =
        setSortOptions(sortOptions.copy(isDirectoriesFirst = isDirectoriesFirst))

    private fun setSortOptions(sortOptions: FileSortOptions) {
        val pathSortOptions = _pathSettings.value!!.sortOptions
        if (pathSortOptions.value != null) {
            pathSortOptions.putValue(sortOptions)
        } else {
            Settings.FILE_LIST_SORT_OPTIONS.putValue(sortOptions)
        }
    }

    val viewSortPathSpecificFlow: StateFlow<Boolean> = _pathSettings.filterNotNull()
        .flatMapLatest { pathSettings ->
            combine(pathSettings.viewType.flow, pathSettings.sortOptions.flow) {
                pathViewType, pathSortOptions -> pathViewType != null || pathSortOptions != null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    var isViewSortPathSpecific: Boolean
        get() = _pathSettings.value?.let {
            it.viewType.value != null || it.sortOptions.value != null
        } ?: false
        set(value) {
            val pathSettings = _pathSettings.value!!
            if (value) {
                if (pathSettings.viewType.value == null) {
                    pathSettings.viewType.putValue(Settings.FILE_LIST_VIEW_TYPE.value)
                }
                if (pathSettings.sortOptions.value == null) {
                    pathSettings.sortOptions.putValue(Settings.FILE_LIST_SORT_OPTIONS.value)
                }
            } else {
                if (pathSettings.viewType.value != null) {
                    pathSettings.viewType.putValue(null)
                }
                if (pathSettings.sortOptions.value != null) {
                    pathSettings.sortOptions.putValue(null)
                }
            }
        }

    /**
     * The files as shown: without hidden files unless they are shown, and sorted unless they are
     * search results, which stay in the order they are found.
     */
    val displayedFileListFlow: StateFlow<List<FileItem>?> = combine(
        _fileListFlow, sortOptionsFlow, Settings.FILE_LIST_SHOW_HIDDEN_FILES.flow, _searchStateFlow
    ) { fileList, sortOptions, isShowingHiddenFiles, searchState ->
        getDisplayedFiles(fileList, sortOptions, isShowingHiddenFiles, searchState)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val displayedFiles: List<FileItem>
        get() = getDisplayedFiles(
            fileListStateful, sortOptions, Settings.FILE_LIST_SHOW_HIDDEN_FILES.value, searchState
        ).orEmpty()

    private fun getDisplayedFiles(
        fileList: Stateful<List<FileItem>>,
        sortOptions: FileSortOptions,
        isShowingHiddenFiles: Boolean,
        searchState: SearchState
    ): List<FileItem>? {
        var files = fileList.value ?: return null
        if (!isShowingHiddenFiles) {
            files = files.filterNot { it.isHidden }
        }
        if (!searchState.isSearching) {
            files = files.sortedWith(sortOptions.createComparator())
        }
        return files
    }

    private val _pickOptionsFlow = MutableStateFlow<PickOptions?>(null)
    val pickOptionsFlow: StateFlow<PickOptions?>
        get() = _pickOptionsFlow
    var pickOptions: PickOptions?
        get() = _pickOptionsFlow.value
        set(value) {
            _pickOptionsFlow.value = value
        }

    private val _selectedFilesFlow = MutableStateFlow(fileItemSetOf())
    val selectedFilesFlow: StateFlow<FileItemSet>
        get() = _selectedFilesFlow
    val selectedFiles: FileItemSet
        get() = _selectedFilesFlow.value

    fun selectFile(file: FileItem, selected: Boolean) {
        selectFiles(fileItemSetOf(file), selected)
    }

    fun selectFiles(files: FileItemSet, selected: Boolean) {
        val newSelectedFiles = FileItemSet().apply { addAll(selectedFiles) }
        var changed = false
        for (file in files) {
            changed = changed or if (selected) {
                newSelectedFiles.add(file)
            } else {
                newSelectedFiles.remove(file)
            }
        }
        if (changed) {
            _selectedFilesFlow.value = newSelectedFiles
        }
    }

    fun clearSelectedFiles() {
        if (selectedFiles.isEmpty()) {
            return
        }
        _selectedFilesFlow.value = fileItemSetOf()
    }

    val pasteStateFlow: StateFlow<PasteState>
        get() = _pasteStateFlow
    val pasteState: PasteState
        get() = _pasteStateFlow.value

    fun addToPasteState(copy: Boolean, files: FileItemSet) {
        val pasteState = pasteState
        val newFiles = FileItemSet()
        if (pasteState.copy == copy) {
            newFiles.addAll(pasteState.files)
        }
        val changed = newFiles.addAll(files) ||
            (pasteState.copy != copy && pasteState.files.isNotEmpty())
        if (changed) {
            _pasteStateFlow.value = PasteState(copy, newFiles)
        }
    }

    fun clearPasteState() {
        val pasteState = pasteState
        if (pasteState.files.isEmpty()) {
            return
        }
        _pasteStateFlow.value = PasteState(pasteState.copy, fileItemSetOf())
    }

    var isStorageAccessRequested = false

    companion object {
        private const val SEARCH_INTERVAL_MILLIS = 500L

        // Shared by all the windows, like the clipboard.
        private val _pasteStateFlow = MutableStateFlow(PasteState())
    }
}

// The trail shown under the navigation root it's in, like a storage, rather than from the root of
// the file system.
private fun createBreadcrumbData(
    trailData: TrailData,
    navigationRootMap: Map<Path, NavigationRoot>
): BreadcrumbData {
    val paths = mutableListOf<Path>()
    val nameProducers = mutableListOf<(Context) -> String>()
    var selectedIndex = trailData.currentIndex
    for (path in trailData.trail) {
        val navigationRoot = navigationRootMap[path]
        val itemCount = nameProducers.size
        if (navigationRoot != null && selectedIndex >= itemCount) {
            selectedIndex -= itemCount
            paths.clear()
            paths.add(navigationRoot.path)
            nameProducers.clear()
            nameProducers.add { navigationRoot.getName(it) }
        } else {
            paths.add(path)
            nameProducers.add { path.name }
        }
    }
    return BreadcrumbData(paths, nameProducers, selectedIndex)
}

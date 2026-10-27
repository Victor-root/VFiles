/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import java8.nio.file.AccessDeniedException
import java8.nio.file.NoSuchFileException
import java8.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.navigation.NavigationDrawerContent
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.StorageVolumeListLiveData
import me.zhanghai.android.files.theme.StatusBarBackgroundEffect
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.FastScroller
import me.zhanghai.android.files.ui.IconBitmapCache
import me.zhanghai.android.files.ui.LocalIconBitmapCache
import me.zhanghai.android.files.ui.SpeedDial
import me.zhanghai.android.files.ui.SpeedDialAction
import me.zhanghai.android.files.ui.SpeedDialScrim
import me.zhanghai.android.files.ui.asFastScrollable
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.findCauseByClass
import me.zhanghai.android.files.util.isTelevision
import me.zhanghai.android.files.util.observeAsStateCompat
import me.zhanghai.android.files.util.showToast
import java.util.Locale
import kotlin.math.roundToInt

private const val SEARCH_DEBOUNCE_MILLIS = 1000L
private val GRID_COLUMN_MIN_WIDTH = 180.dp

@Composable
fun FileListScreen(controller: FileListController, viewModel: FileListViewModel) {
    // These are computed once observed, so there's nothing to show for the first frame.
    val currentPath = viewModel.currentPathLiveData.observeAsState().value ?: return
    val breadcrumbData = viewModel.breadcrumbLiveData.observeAsState().value ?: return
    val fileListStateful = viewModel.fileListLiveData.observeAsState().value ?: return
    val viewType = viewModel.viewTypeLiveData.observeAsState().value ?: return
    val sortOptions = viewModel.sortOptionsLiveData.observeAsState().value ?: return
    val isViewSortPathSpecific =
        viewModel.viewSortPathSpecificLiveData.observeAsState().value ?: return
    val files = viewModel.displayedFileListLiveData.observeAsState().value
    val pickOptions by viewModel.pickOptionsLiveData.observeAsState()
    val selectedFiles by viewModel.selectedFilesLiveData.observeAsStateCompat()
    val pasteState by viewModel.pasteStateLiveData.observeAsStateCompat()
    val searchState by viewModel.searchStateLiveData.observeAsStateCompat()
    val isSearchViewExpanded by viewModel.searchViewExpandedLiveData.observeAsStateCompat()
    val showHiddenFiles by Settings.FILE_LIST_SHOW_HIDDEN_FILES.observeAsStateCompat()
    val isPersistentDrawerOpen by Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.observeAsStateCompat()

    val context = LocalContext.current
    val inputModeManager = LocalInputModeManager.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    // Large screens in landscape keep the drawer next to the list.
    val hasPersistentDrawer = configuration.smallestScreenWidthDp >= 600 &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var drawerSubtitleKey by remember { mutableIntStateOf(0) }
    var drawerFocusRequest by remember { mutableIntStateOf(0) }
    val listFocusRequester = remember { FocusRequester() }
    val navigationIconFocusRequester = remember { FocusRequester() }
    var isSpeedDialExpanded by rememberSaveable { mutableStateOf(false) }
    val searchQueryState = rememberTextFieldState(viewModel.searchViewQuery)
    val isKeyboardMode = inputModeManager.inputMode == InputMode.Keyboard

    LifecycleResumeEffect(controller) {
        // A volume plugged in while in the background should appear in the drawer.
        StorageVolumeListLiveData.refresh()
        controller.ensureStorageAccess()
        onPauseOrDispose {}
    }

    // Searches once the query stops changing for a moment.
    LaunchedEffect(isSearchViewExpanded) {
        if (!isSearchViewExpanded) {
            return@LaunchedEffect
        }
        snapshotFlow { searchQueryState.text.toString() }.collectLatest { query ->
            viewModel.searchViewQuery = query
            delay(SEARCH_DEBOUNCE_MILLIS)
            if (query.isNotEmpty()) {
                viewModel.search(query)
            }
        }
    }

    val hasFiles = !fileListStateful.value.isNullOrEmpty()
    LaunchedEffect(fileListStateful) {
        when (fileListStateful) {
            is Failure -> {
                fileListStateful.throwable.printStackTrace()
                if (hasFiles) {
                    context.showToast(fileListStateful.throwable.errorMessageRes)
                }
            }
            is Success -> {
                controller.restorePendingScrollState()
                // A finished operation may have changed the free space shown in the drawer.
                drawerSubtitleKey++
            }
            is Loading -> {}
        }
    }
    // On a D-pad, the focus goes back to the list in a newly opened folder, or to the app bar when
    // the folder is empty.
    val isFileListLoaded = fileListStateful is Success
    LaunchedEffect(currentPath, isFileListLoaded) {
        if (!isFileListLoaded || !isKeyboardMode || drawerState.isOpen) {
            return@LaunchedEffect
        }
        if (hasFiles) {
            listFocusRequester.requestFocus()
        } else {
            navigationIconFocusRequester.requestFocus()
        }
    }

    val currentHasFiles by rememberUpdatedState(hasFiles)
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.targetValue }.drop(1).collect {
            val isKeyboardInput = inputModeManager.inputMode == InputMode.Keyboard
            if (it == DrawerValue.Open) {
                drawerSubtitleKey++
                if (isKeyboardInput) {
                    drawerFocusRequest++
                }
            } else if (isKeyboardInput && currentHasFiles) {
                listFocusRequester.requestFocus()
            }
        }
    }

    val openDrawer = {
        if (hasPersistentDrawer) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.putValue(!isPersistentDrawerOpen)
        } else {
            coroutineScope.launch { drawerState.open() }
        }
    }
    val closeDrawer: () -> Unit = { coroutineScope.launch { drawerState.close() } }
    val drawerContent = @Composable { modifier: Modifier ->
        NavigationDrawerContent(
            currentPath = currentPath,
            onNavigateTo = controller::navigateTo,
            onNavigateToRoot = controller::navigateToRoot,
            onCloseDrawer = closeDrawer,
            subtitleKey = drawerSubtitleKey,
            focusCheckedItemRequest = drawerFocusRequest,
            modifier = modifier
        )
    }

    val screen = @Composable {
        FileListScaffold(
            controller = controller,
            viewModel = viewModel,
            currentPath = currentPath,
            breadcrumbData = breadcrumbData,
            fileListStateful = fileListStateful,
            files = files,
            viewType = viewType,
            sortOptions = sortOptions,
            isViewSortPathSpecific = isViewSortPathSpecific,
            pickOptions = pickOptions,
            selectedFiles = selectedFiles,
            pasteState = pasteState,
            isSearching = searchState.isSearching,
            isSearchViewExpanded = isSearchViewExpanded,
            searchQueryState = searchQueryState,
            showHiddenFiles = showHiddenFiles,
            isSpeedDialExpanded = isSpeedDialExpanded,
            onSpeedDialExpandedChange = { isSpeedDialExpanded = it },
            onNavigationIconClick = { openDrawer() },
            // A D-pad left from a file in the first column opens the drawer.
            onFileListLeftEdgeReached = {
                if (!hasPersistentDrawer) {
                    openDrawer()
                }
            },
            listFocusRequester = listFocusRequester,
            navigationIconFocusRequester = navigationIconFocusRequester,
            persistentDrawer = if (hasPersistentDrawer) {
                { drawerContent(it) }
            } else {
                null
            },
            isPersistentDrawerOpen = isPersistentDrawerOpen
        )
    }

    Box(modifier = Modifier.onKeyEvent(controller::onKeyShortcut)) {
        if (hasPersistentDrawer) {
            screen()
        } else {
            FileListModalDrawer(drawerState, drawerContent, screen)
        }
    }

    controller.dialog?.let { dialog ->
        FileListDialogHost(dialog, controller) { controller.dialog = null }
    }

    // Declared last, in increasing priority.
    BackHandler(enabled = breadcrumbData.selectedIndex > 0) { viewModel.navigateUp() }
    BackHandler(enabled = isSearchViewExpanded) { controller.collapseSearch() }
    BackHandler(enabled = selectedFiles.isNotEmpty()) { controller.clearSelectedFiles() }
    BackHandler(enabled = isSpeedDialExpanded) { isSpeedDialExpanded = false }
    BackHandler(enabled = !hasPersistentDrawer && drawerState.isOpen) { closeDrawer() }
}

private val DRAWER_MAX_WIDTH = 320.dp
private val DRAWER_SCREEN_EDGE_MARGIN = 56.dp

@Composable
private fun FileListModalDrawer(
    drawerState: DrawerState,
    drawerContent: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isOpening = drawerState.targetValue == DrawerValue.Open
    if (isOpening) {
        StatusBarBackgroundEffect(MaterialTheme.colorScheme.surface)
    }
    val windowWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(min(windowWidth - DRAWER_SCREEN_EDGE_MARGIN, DRAWER_MAX_WIDTH))
                    // The D-pad stays in the drawer while it's open, and right closes it.
                    .focusProperties { onEnter = { if (!isOpening) cancelFocusChange() } }
                    .focusGroup()
                    .onPreviewKeyEvent {
                        val isRight = it.type == KeyEventType.KeyDown &&
                            it.key == Key.DirectionRight
                        if (isRight) {
                            coroutineScope.launch { drawerState.close() }
                        }
                        isRight
                    },
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                drawerContent(Modifier.fillMaxHeight())
            }
        },
        drawerState = drawerState
    ) {
        Box(
            modifier = Modifier
                .focusProperties { onEnter = { if (isOpening) cancelFocusChange() } }
                .focusGroup()
        ) {
            content()
        }
    }
}

@Composable
private fun FileListScaffold(
    controller: FileListController,
    viewModel: FileListViewModel,
    currentPath: Path,
    breadcrumbData: BreadcrumbData,
    fileListStateful: Stateful<List<FileItem>>,
    files: List<FileItem>?,
    viewType: FileViewType,
    sortOptions: FileSortOptions,
    isViewSortPathSpecific: Boolean,
    pickOptions: PickOptions?,
    selectedFiles: FileItemSet,
    pasteState: PasteState,
    isSearching: Boolean,
    isSearchViewExpanded: Boolean,
    searchQueryState: TextFieldState,
    showHiddenFiles: Boolean,
    isSpeedDialExpanded: Boolean,
    onSpeedDialExpandedChange: (Boolean) -> Unit,
    onNavigationIconClick: () -> Unit,
    onFileListLeftEdgeReached: () -> Unit,
    listFocusRequester: FocusRequester,
    navigationIconFocusRequester: FocusRequester,
    persistentDrawer: (@Composable (Modifier) -> Unit)?,
    isPersistentDrawerOpen: Boolean
) {
    val gridState = controller.gridState
    val isScrolled by remember(gridState) { derivedStateOf { gridState.canScrollBackward } }
    val appBarColors = fileListAppBarColors(isScrolled)
    // Edge to edge, the app bar hides along with the status bar area when scrolling down, and
    // comes back when scrolling up, except while selecting or searching.
    val isEdgeToEdge by Settings.EDGE_TO_EDGE.observeAsStateCompat()
    val isAppBarPinned = !isEdgeToEdge || selectedFiles.isNotEmpty() || isSearchViewExpanded
    val appBarScrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { !isAppBarPinned })
    val appBarState = appBarScrollBehavior.state
    LaunchedEffect(currentPath, isAppBarPinned) { appBarState.heightOffset = 0f }
    // The status bar icons follow the list once the app bar is mostly gone from behind them.
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    val isAppBarBehindStatusBar by remember(appBarState, statusBarHeight) {
        derivedStateOf {
            appBarState.heightOffset - appBarState.heightOffsetLimit >= statusBarHeight / 2f
        }
    }
    if (!isAppBarBehindStatusBar) {
        StatusBarBackgroundEffect(MaterialTheme.colorScheme.background)
    }
    AppScaffold(
        modifier = Modifier.nestedScroll(appBarScrollBehavior.nestedScrollConnection),
        topBar = {
            Surface(color = appBarColors.container, contentColor = appBarColors.content) {
                Column(
                    modifier = Modifier
                        .hideOnScroll(appBarState)
                        .onFocusChanged { if (it.hasFocus) appBarState.heightOffset = 0f }
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                ) {
                    AnimatedContent(
                        targetState = selectedFiles.isNotEmpty(),
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { isSelecting ->
                        when {
                            isSelecting ->
                                FileListSelectionAppBar(
                                    selectedFiles, pickOptions, currentPath, controller,
                                    appBarColors
                                )
                            isSearchViewExpanded ->
                                FileListSearchAppBar(
                                    queryState = searchQueryState,
                                    colors = appBarColors,
                                    onSearch = {
                                        viewModel.search(searchQueryState.text.toString())
                                    },
                                    onCollapse = controller::collapseSearch
                                ) { FileListOverflowMenu(controller, showHiddenFiles) }
                            else ->
                                FileListMainAppBar(
                                    controller = controller,
                                    viewModel = viewModel,
                                    fileListStateful = fileListStateful,
                                    isSearching = isSearching,
                                    viewType = viewType,
                                    sortOptions = sortOptions,
                                    isViewSortPathSpecific = isViewSortPathSpecific,
                                    pickOptions = pickOptions,
                                    showHiddenFiles = showHiddenFiles,
                                    colors = appBarColors,
                                    onNavigationIconClick = onNavigationIconClick,
                                    navigationIconFocusRequester = navigationIconFocusRequester
                                )
                        }
                    }
                    val context = LocalContext.current
                    Breadcrumb(
                        data = breadcrumbData,
                        isReadOnly = remember(context) { context.isTelevision },
                        onNavigateTo = controller::navigateTo,
                        onCopyPath = controller::copyPath,
                        onOpenInNewTask = controller::openInNewTask,
                        contentPadding = PaddingValues(
                            start = dimensionResource(R.dimen.content_start_margin_minus_12dp),
                            end = dimensionResource(R.dimen.screen_edge_margin_minus_12dp)
                        )
                    )
                }
            }
        },
        bottomBar = {
            FileListBottomBars(pickOptions, pasteState, currentPath, controller, viewModel)
        },
        floatingActionButton = {
            SpeedDial(
                isExpanded = isSpeedDialExpanded,
                onExpandedChange = onSpeedDialExpandedChange,
                iconRes = R.drawable.add_icon_white_24dp,
                contentDescription = stringResource(R.string.file_list_action_create),
                actions = listOf(
                    SpeedDialAction(
                        R.drawable.directory_icon_white_24dp,
                        stringResource(R.string.file_list_action_create_directory),
                        controller::showCreateDirectoryDialog
                    ),
                    SpeedDialAction(
                        R.drawable.file_icon_white_24dp,
                        stringResource(R.string.file_list_action_create_file),
                        controller::showCreateFileDialog
                    )
                )
            )
        }
    ) { contentPadding ->
        Row(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
            if (persistentDrawer != null) {
                AnimatedVisibility(
                    visible = isPersistentDrawerOpen,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally()
                ) {
                    Surface(modifier = Modifier.width(DRAWER_MAX_WIDTH)) {
                        persistentDrawer(Modifier.fillMaxHeight())
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                FileListContent(
                    controller = controller,
                    fileListStateful = fileListStateful,
                    files = files,
                    currentPath = currentPath,
                    viewType = viewType,
                    sortOptions = sortOptions,
                    pickOptions = pickOptions,
                    selectedFiles = selectedFiles,
                    isSearching = isSearching,
                    gridState = gridState,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onLeftEdgeReached = onFileListLeftEdgeReached,
                    focusRequester = listFocusRequester
                )
                SpeedDialScrim(
                    isExpanded = isSpeedDialExpanded,
                    onCollapse = { onSpeedDialExpandedChange(false) }
                )
                FileJobProgressButton(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = contentPadding.calculateBottomPadding())
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

// Shrinks the content from its top as the state's height offset goes down to its limit.
private fun Modifier.hideOnScroll(state: TopAppBarState): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable =
            measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val heightOffsetLimit = -placeable.height.toFloat()
        if (state.heightOffsetLimit != heightOffsetLimit) {
            state.heightOffsetLimit = heightOffsetLimit
        }
        val height = (placeable.height + state.heightOffset).roundToInt().coerceAtLeast(0)
        layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
    }

@Composable
private fun FileListMainAppBar(
    controller: FileListController,
    viewModel: FileListViewModel,
    fileListStateful: Stateful<List<FileItem>>,
    isSearching: Boolean,
    viewType: FileViewType,
    sortOptions: FileSortOptions,
    isViewSortPathSpecific: Boolean,
    pickOptions: PickOptions?,
    showHiddenFiles: Boolean,
    colors: FileListAppBarColors,
    onNavigationIconClick: () -> Unit,
    navigationIconFocusRequester: FocusRequester
) {
    FileListTopAppBar(
        title = fileListTitle(pickOptions, controller),
        subtitle = fileListSubtitle(fileListStateful, isSearching),
        isTitleScrolling = pickOptions != null,
        colors = colors,
        navigationIcon = {
            // When picking for another app, closing cancels the pick.
            if (pickOptions != null) {
                IconButton(
                    onClick = controller::cancelPicking,
                    modifier = Modifier.focusRequester(navigationIconFocusRequester)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close_icon_white_24dp),
                        contentDescription = stringResource(android.R.string.cancel)
                    )
                }
            } else {
                IconButton(
                    onClick = onNavigationIconClick,
                    modifier = Modifier.focusRequester(navigationIconFocusRequester)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.menu_icon_control_normal_24dp),
                        contentDescription = stringResource(R.string.open_navigation_drawer)
                    )
                }
            }
        }
    ) {
        ActionButton(R.drawable.search_icon_control_normal_24dp, stringResource(R.string.search)) {
            viewModel.isSearchViewExpanded = true
        }
        ViewSortMenuButton(viewType, sortOptions, isViewSortPathSpecific, viewModel)
        FileListOverflowMenu(controller, showHiddenFiles)
    }
}

@Composable
private fun fileListTitle(pickOptions: PickOptions?, controller: FileListController): String {
    if (pickOptions == null) {
        return stringResource(R.string.file_list_title)
    }
    val count = if (pickOptions.allowMultiple) Int.MAX_VALUE else 1
    val title = when (pickOptions.mode) {
        PickOptions.Mode.OPEN_FILE ->
            pluralStringResource(R.plurals.file_list_title_open_file, count)
        PickOptions.Mode.CREATE_FILE -> stringResource(R.string.file_list_title_create_file)
        PickOptions.Mode.OPEN_DIRECTORY ->
            pluralStringResource(R.plurals.file_list_title_open_directory, count)
    }
    // Makes it clear that it's a picker opened by another app.
    val callingAppLabel = remember(controller) { controller.callingAppLabel }
    return if (callingAppLabel != null) "$title · $callingAppLabel" else title
}

@Composable
private fun fileListSubtitle(fileListStateful: Stateful<List<FileItem>>, isSearching: Boolean) =
    when {
        fileListStateful is Failure -> stringResource(R.string.error)
        fileListStateful is Loading && !isSearching -> stringResource(R.string.loading)
        else -> {
            val files = fileListStateful.value!!
            val directoryCount = files.count { it.attributes.isDirectory }
            val fileCount = files.size - directoryCount
            val counts = listOfNotNull(
                if (directoryCount > 0) {
                    pluralStringResource(
                        R.plurals.file_list_subtitle_directory_count_format, directoryCount,
                        directoryCount
                    )
                } else {
                    null
                },
                if (fileCount > 0) {
                    pluralStringResource(
                        R.plurals.file_list_subtitle_file_count_format, fileCount, fileCount
                    )
                } else {
                    null
                }
            )
            if (counts.isNotEmpty()) {
                counts.joinToString(stringResource(R.string.file_list_subtitle_separator))
            } else {
                stringResource(R.string.empty)
            }
        }
    }

@Composable
private fun FileListBottomBars(
    pickOptions: PickOptions?,
    pasteState: PasteState,
    currentPath: Path,
    controller: FileListController,
    viewModel: FileListViewModel
) {
    val bar = when {
        pickOptions?.mode == PickOptions.Mode.CREATE_FILE -> BottomBar.CREATE_FILE
        pickOptions?.mode == PickOptions.Mode.OPEN_DIRECTORY -> BottomBar.OPEN_DIRECTORY
        pickOptions == null && pasteState.files.isNotEmpty() -> BottomBar.PASTE
        else -> null
    }
    AnimatedContent(
        targetState = bar,
        transitionSpec = { slideInVertically { it } togetherWith slideOutVertically { it } }
    ) {
        when (it) {
            BottomBar.CREATE_FILE -> CreateFileBar(controller)
            BottomBar.OPEN_DIRECTORY -> OpenDirectoryBar(currentPath, controller)
            BottomBar.PASTE ->
                PasteBar(pasteState, currentPath, controller, onClose = viewModel::clearPasteState)
            null -> {}
        }
    }
}

private enum class BottomBar {
    CREATE_FILE,
    OPEN_DIRECTORY,
    PASTE
}

private val Throwable.errorMessageRes: Int
    get() = when {
        findCauseByClass<NoSuchFileException>() != null -> R.string.file_list_error_folder_not_found
        findCauseByClass<AccessDeniedException>() != null ->
            R.string.file_list_error_permission_denied
        else -> R.string.file_list_error_unknown
    }

@Composable
private fun FileListContent(
    controller: FileListController,
    fileListStateful: Stateful<List<FileItem>>,
    files: List<FileItem>?,
    currentPath: Path,
    viewType: FileViewType,
    sortOptions: FileSortOptions,
    pickOptions: PickOptions?,
    selectedFiles: FileItemSet,
    isSearching: Boolean,
    gridState: LazyGridState,
    bottomPadding: Dp,
    onLeftEdgeReached: () -> Unit,
    focusRequester: FocusRequester
) {
    val hasFiles = !fileListStateful.value.isNullOrEmpty()
    val isLoading = fileListStateful is Loading
    PullToRefreshBox(
        isRefreshing = isLoading && (hasFiles || isSearching),
        onRefresh = controller::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (files != null) {
            FileGrid(
                controller = controller,
                files = files,
                currentPath = currentPath,
                viewType = viewType,
                sortOptions = sortOptions,
                pickOptions = pickOptions,
                selectedFiles = selectedFiles,
                isSearching = isSearching,
                gridState = gridState,
                bottomPadding = bottomPadding,
                onLeftEdgeReached = onLeftEdgeReached,
                focusRequester = focusRequester
            )
        }
        AnimatedVisibility(
            visible = isLoading && !(hasFiles || isSearching),
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CircularProgressIndicator()
        }
        val errorRes = (fileListStateful as? Failure)?.throwable?.errorMessageRes
            ?.takeIf { !hasFiles }
        AnimatedContent(
            targetState = errorRes,
            modifier = Modifier.align(Alignment.Center),
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) {
            if (it != null) {
                FileListMessage(R.drawable.error_icon_240dp, stringResource(it))
            }
        }
        AnimatedVisibility(
            visible = fileListStateful is Success && !hasFiles,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FileListMessage(R.drawable.file_icon_240dp, stringResource(R.string.file_list_empty))
        }
    }
}

@Composable
private fun FileListMessage(iconRes: Int, text: String) {
    Column(
        modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(240.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun FileGrid(
    controller: FileListController,
    files: List<FileItem>,
    currentPath: Path,
    viewType: FileViewType,
    sortOptions: FileSortOptions,
    pickOptions: PickOptions?,
    selectedFiles: FileItemSet,
    isSearching: Boolean,
    gridState: LazyGridState,
    bottomPadding: Dp,
    onLeftEdgeReached: () -> Unit,
    focusRequester: FocusRequester
) {
    val context = LocalContext.current
    val nameEllipsize by Settings.FILE_NAME_ELLIPSIZE.observeAsStateCompat()
    val isAnimationEnabled by Settings.FILE_LIST_ANIMATION.observeAsStateCompat()
    // Files appear with an animation when a new list is shown, not when it's only updated.
    val appearance = remember(currentPath, isSearching, viewType, sortOptions) {
        ListAppearance(isAnimationEnabled)
    }
    LaunchedEffect(appearance) { appearance.stopStarting() }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val iconBitmapCache = remember(context) { IconBitmapCache(context) }
    CompositionLocalProvider(LocalIconBitmapCache provides iconBitmapCache) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columnCount = when (viewType) {
                FileViewType.LIST -> 1
                FileViewType.GRID -> (maxWidth / GRID_COLUMN_MIN_WIDTH).toInt().coerceAtLeast(2)
            }
            val contentPadding = PaddingValues(
                bottom = bottomPadding + dimensionResource(R.dimen.list_bottom_padding_with_fab)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusRestorer()
                    .onPreviewKeyEvent {
                        val isLeftEdge = it.type == KeyEventType.KeyDown &&
                            it.key == Key.DirectionLeft && focusedIndex != -1 &&
                            focusedIndex % columnCount == 0
                        if (isLeftEdge) {
                            onLeftEdgeReached()
                        }
                        isLeftEdge
                    },
                state = gridState,
                contentPadding = contentPadding
            ) {
                itemsIndexed(
                    files,
                    key = { _, file -> file.path.toString() },
                    contentType = { _, _ -> viewType }
                ) { index, file ->
                    val isEnabled = controller.isFileSelectable(file) || file.attributes.isDirectory
                    val appearanceTime = rememberAppearanceTime(appearance, gridState, index)
                    val itemModifier = Modifier
                        .itemLayer(appearanceTime)
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedIndex = index
                            } else if (focusedIndex == index) {
                                focusedIndex = -1
                            }
                        }
                    when (viewType) {
                        FileViewType.LIST ->
                            FileListItem(
                                file = file,
                                isSelected = file in selectedFiles,
                                isEnabled = isEnabled,
                                isPicking = pickOptions != null,
                                nameEllipsize = nameEllipsize,
                                onClick = { controller.onFileClick(file) },
                                onLongClick = { controller.onFileLongClick(file) },
                                onIconClick = { controller.toggleFileSelection(file) },
                                actions = controller,
                                modifier = itemModifier
                            )
                        FileViewType.GRID ->
                            FileGridItem(
                                file = file,
                                isSelected = file in selectedFiles,
                                isEnabled = isEnabled,
                                isPicking = pickOptions != null,
                                onClick = { controller.onFileClick(file) },
                                onLongClick = { controller.onFileLongClick(file) },
                                onIconClick = { controller.toggleFileSelection(file) },
                                actions = controller,
                                modifier = itemModifier
                            )
                    }
                }
            }
            FastScroller(
                scrollable = gridState.asFastScrollable(),
                modifier = Modifier.align(Alignment.TopEnd),
                contentPadding = contentPadding,
                popupText = {
                    files.getOrNull(gridState.firstVisibleItemIndex)
                        ?.getPopupText(sortOptions, context)
                        .orEmpty()
                }
            )
        }
    }
}

private fun FileItem.getPopupText(
    sortOptions: FileSortOptions,
    context: Context
): String =
    when (sortOptions.by) {
        FileSortOptions.By.NAME -> name.take(1).uppercase(Locale.getDefault())
        FileSortOptions.By.TYPE -> extension.uppercase(Locale.getDefault())
        FileSortOptions.By.SIZE -> attributes.fileSize.formatHumanReadable(context)
        FileSortOptions.By.LAST_MODIFIED ->
            attributes.lastModifiedTime().toInstant().formatShort(context)
    }

private const val APPEARANCE_ALPHA_DURATION_MILLIS = 400
private const val APPEARANCE_DURATION_MILLIS = 500
private const val APPEARANCE_STAGGER_MILLIS = 20
private const val APPEARANCE_TRANSLATION_FRACTION = 0.25f

/**
 * The items laid out in the first frame of a new list fade and slide in one after the other.
 * Scrolling ends the animation.
 */
private class ListAppearance(isEnabled: Boolean) {
    var isStarting by mutableStateOf(isEnabled)
        private set

    suspend fun stopStarting() {
        withFrameNanos {}
        isStarting = false
    }
}

// The time since the item started to appear, from 0 to the duration of the animation, or null for
// an item that appears without animation.
@Composable
private fun rememberAppearanceTime(
    appearance: ListAppearance,
    gridState: LazyGridState,
    index: Int
): Animatable<Float, AnimationVector1D>? {
    val isAppearing = remember(appearance) { appearance.isStarting }
    if (!isAppearing) {
        return null
    }
    val time = remember(appearance) { Animatable(0f) }
    LaunchedEffect(time) {
        val endOnScroll = launch {
            snapshotFlow { gridState.isScrollInProgress }.first { it }
            time.snapTo(APPEARANCE_DURATION_MILLIS.toFloat())
        }
        val position = (index - gridState.firstVisibleItemIndex).coerceAtLeast(0)
        delay((position * APPEARANCE_STAGGER_MILLIS).toLong())
        time.animateTo(
            APPEARANCE_DURATION_MILLIS.toFloat(),
            tween(APPEARANCE_DURATION_MILLIS, easing = LinearEasing)
        )
        endOnScroll.cancel()
    }
    return time
}

// Each item has a layer of its own, so that scrolling only moves the layers instead of drawing
// their content again, which takes long on slow CPU cores because of the text.
private fun Modifier.itemLayer(appearanceTime: Animatable<Float, AnimationVector1D>?): Modifier =
    graphicsLayer {
        val millis = appearanceTime?.value ?: return@graphicsLayer
        alpha = LinearOutSlowInEasing.transform(
            (millis / APPEARANCE_ALPHA_DURATION_MILLIS).coerceAtMost(1f)
        )
        translationY =
            (1 - LinearOutSlowInEasing.transform(millis / APPEARANCE_DURATION_MILLIS)) *
                APPEARANCE_TRANSLATION_FRACTION * size.height
        // Fading without an offscreen buffer per item, which costs a lot of GPU time while a
        // whole list appears.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

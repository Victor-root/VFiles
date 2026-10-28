/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java8.nio.file.AccessDeniedException
import java8.nio.file.NoSuchFileException
import java8.nio.file.Path
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
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
import me.zhanghai.android.files.ui.ActionButton
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.FastScroller
import me.zhanghai.android.files.ui.HideOnScrollTopBar
import me.zhanghai.android.files.ui.IconBitmapCache
import me.zhanghai.android.files.ui.LocalIconBitmapCache
import me.zhanghai.android.files.ui.SpeedDial
import me.zhanghai.android.files.ui.SpeedDialAction
import me.zhanghai.android.files.ui.SpeedDialScrim
import me.zhanghai.android.files.ui.asFastScrollable
import me.zhanghai.android.files.ui.rememberAppBarScrollState
import me.zhanghai.android.files.ui.sampleStatusBarContent
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.findCauseByClass
import me.zhanghai.android.files.util.isTelevision
import me.zhanghai.android.files.util.observeAsStateCompat
import me.zhanghai.android.files.util.showToast
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.max

private const val SEARCH_DEBOUNCE_MILLIS = 1000L
private const val EXIT_BACK_TIMEOUT_MILLIS = 2000L
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
                // A finished operation may have changed the free space shown in the drawer. A
                // hidden drawer reads it again once shown, instead of on each folder opened.
                val isDrawerShown =
                    if (hasPersistentDrawer) isPersistentDrawerOpen else drawerState.isOpen
                if (isDrawerShown) {
                    drawerSubtitleKey++
                }
            }
            is Loading -> {}
        }
    }
    val currentHasFiles by rememberUpdatedState(hasFiles)
    // On a D-pad, the focus goes to the list, or to the drawer button in an empty folder.
    val focusContent = {
        if (currentHasFiles) {
            listFocusRequester.requestFocus()
        } else {
            navigationIconFocusRequester.requestFocus()
        }
    }
    // It moves there in a newly opened folder.
    val isFileListLoaded = fileListStateful is Success
    LaunchedEffect(currentPath, isFileListLoaded) {
        if (!isFileListLoaded || !isKeyboardMode || drawerState.isOpen) {
            return@LaunchedEffect
        }
        focusContent()
    }

    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.targetValue }.drop(1).collect {
            if (it == DrawerValue.Open) {
                drawerSubtitleKey++
                if (inputModeManager.inputMode == InputMode.Keyboard) {
                    drawerFocusRequest++
                }
            }
        }
    }
    // Only once the drawer is closed, since its items keep the focus while it closes, which
    // doesn't happen until coming back when it was closed to open another screen.
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue }.drop(1).collect {
            if (it == DrawerValue.Closed && inputModeManager.inputMode == InputMode.Keyboard) {
                focusContent()
            }
        }
    }
    // Whenever nothing is focused on a D-pad, like when the focused file went away with its folder,
    // or when coming back from another screen.
    var hasFocus by remember { mutableStateOf(false) }
    val currentIsFileListLoaded by rememberUpdatedState(isFileListLoaded)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, drawerState) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { !hasFocus && currentIsFileListLoaded && drawerState.isClosed }
                .collect {
                    if (it && inputModeManager.inputMode == InputMode.Keyboard) {
                        focusContent()
                    }
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

    Box(
        modifier = Modifier
            .onFocusChanged { hasFocus = it.hasFocus }
            .onKeyEvent(controller::onKeyShortcut)
    ) {
        if (hasPersistentDrawer) {
            screen()
        } else {
            FileListModalDrawer(drawerState, drawerContent, screen)
        }
    }

    controller.dialog?.let { dialog ->
        FileListDialogHost(dialog, controller) { controller.dialog = null }
    }

    // At the start of the app, back has to be pressed twice in a row to leave it.
    val isDoubleBackToExit by Settings.DOUBLE_BACK_TO_EXIT.observeAsStateCompat()
    var lastBackTime by remember { mutableStateOf<Long?>(null) }
    val activity = LocalActivity.current
    BackHandler(enabled = isDoubleBackToExit && pickOptions == null) {
        val time = SystemClock.uptimeMillis()
        val isBackAgain = lastBackTime.let { it != null && time - it < EXIT_BACK_TIMEOUT_MILLIS }
        if (activity?.isTaskRoot != true || isBackAgain) {
            activity?.finish()
        } else {
            lastBackTime = time
            context.showToast(R.string.file_list_press_back_again_to_exit)
        }
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
    // comes back when scrolling up, except while selecting or searching, next to a persistent
    // drawer, or on a TV, where the items focused with the D-pad must never be behind it.
    val isEdgeToEdge by Settings.EDGE_TO_EDGE.observeAsStateCompat()
    val context = LocalContext.current
    val isTelevision = remember(context) { context.isTelevision }
    // The list then scrolls behind the app bar, so that it only moves instead of resizing the list.
    val isListBehindAppBar = isEdgeToEdge && persistentDrawer == null && !isTelevision
    val isAppBarPinned =
        !isListBehindAppBar || selectedFiles.isNotEmpty() || isSearchViewExpanded
    val speedDialFocusRequester = remember { FocusRequester() }
    val appBarState = rememberAppBarScrollState(gridState, isEnabled = !isAppBarPinned)
    LaunchedEffect(currentPath) { appBarState.offset = 0f }
    AppScaffold(
        modifier = Modifier.nestedScroll(appBarState.nestedScrollConnection),
        isContentBehindTopBar = true,
        topBar = {
            HideOnScrollTopBar(appBarState) {
                Surface(
                    color = appBarColors.container,
                    contentColor = appBarColors.content
                ) {
                    Column(
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.statusBars.only(WindowInsetsSides.Top)
                        )
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
                        Breadcrumb(
                            data = breadcrumbData,
                            isReadOnly = isTelevision,
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
            }
        },
        bottomBar = {
            FileListBottomBars(pickOptions, pasteState, currentPath, controller, viewModel)
        },
        floatingActionButton = {
            SpeedDial(
                isExpanded = isSpeedDialExpanded,
                // On a D-pad, up and left from the collapsed button go back to the list rather
                // than to the app bar, and there's nothing on its right.
                buttonModifier = Modifier
                    .focusRequester(speedDialFocusRequester)
                    .focusProperties {
                        right = FocusRequester.Cancel
                        if (!fileListStateful.value.isNullOrEmpty()) {
                            left = listFocusRequester
                            if (!isSpeedDialExpanded) {
                                up = listFocusRequester
                            }
                        }
                    },
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
        val appBarHeight = contentPadding.calculateTopPadding()
        Row(
            modifier = Modifier
                .padding(top = if (isListBehindAppBar) 0.dp else appBarHeight)
                .sampleStatusBarContent(appBarState)
        ) {
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
                    topPadding = if (isListBehindAppBar) appBarHeight else 0.dp,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onLeftEdgeReached = onFileListLeftEdgeReached,
                    // On a D-pad, up from the first row goes to the drawer button rather than to
                    // the button right above it, and right from a menu at the end goes to the add
                    // button.
                    onTopEdgeReached = { navigationIconFocusRequester.requestFocus() },
                    onRightEdgeReached = { speedDialFocusRequester.requestFocus() },
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
    topPadding: Dp,
    bottomPadding: Dp,
    onLeftEdgeReached: () -> Unit,
    onTopEdgeReached: () -> Unit,
    onRightEdgeReached: () -> Unit,
    focusRequester: FocusRequester
) {
    val hasFiles = !fileListStateful.value.isNullOrEmpty()
    val isLoading = fileListStateful is Loading
    val isRefreshing = isLoading && (hasFiles || isSearching)
    // Kept from a folder to the next one, unlike the grid which is gone while a folder loads.
    val context = LocalContext.current
    val iconBitmapCache = remember(context) { IconBitmapCache(context) }
    val pullToRefreshState = rememberPullToRefreshState()
    val isTelevision = remember(context) { context.isTelevision }
    // The list scrolls behind the app bar, so what's shown over it is moved below the app bar.
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Pulling needs touch, and D-pad scrolling shouldn't start one on TVs.
            .pullToRefresh(
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
                enabled = !isTelevision,
                onRefresh = controller::refresh
            )
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
                iconBitmapCache = iconBitmapCache,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                onLeftEdgeReached = onLeftEdgeReached,
                onTopEdgeReached = onTopEdgeReached,
                onRightEdgeReached = onRightEdgeReached,
                focusRequester = focusRequester
            )
        }
        AnimatedVisibility(
            visible = isLoading && !(hasFiles || isSearching),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = topPadding),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CircularProgressIndicator()
        }
        val errorRes = (fileListStateful as? Failure)?.throwable?.errorMessageRes
            ?.takeIf { !hasFiles }
        AnimatedContent(
            targetState = errorRes,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = topPadding),
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) {
            if (it != null) {
                FileListMessage(R.drawable.error_icon_240dp, stringResource(it))
            }
        }
        AnimatedVisibility(
            visible = fileListStateful is Success && !hasFiles,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = topPadding),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FileListMessage(R.drawable.file_icon_240dp, stringResource(R.string.file_list_empty))
        }
        PullToRefreshDefaults.Indicator(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding)
        )
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
    iconBitmapCache: IconBitmapCache,
    topPadding: Dp,
    bottomPadding: Dp,
    onLeftEdgeReached: () -> Unit,
    onTopEdgeReached: () -> Unit,
    onRightEdgeReached: () -> Unit,
    focusRequester: FocusRequester
) {
    val context = LocalContext.current
    val nameEllipsize by Settings.FILE_NAME_ELLIPSIZE.observeAsStateCompat()
    val isAnimationEnabled by Settings.FILE_LIST_ANIMATION.observeAsStateCompat()
    // Files appear with an animation when a new list is shown, not when it's only updated.
    val appearance = remember(currentPath, isSearching, viewType, sortOptions) {
        ListAppearance(isAnimationEnabled)
    }
    LaunchedEffect(appearance) { appearance.animate(gridState) }
    // The item focused as a whole, and the one with the focus in it, like on its menu button.
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var focusedWithinIndex by remember { mutableIntStateOf(-1) }
    // Formatted once per list, instead of each time an item scrolls back into view.
    val descriptions = remember(files, context) { IdentityHashMap<FileItem, String>() }
    CompositionLocalProvider(LocalIconBitmapCache provides iconBitmapCache) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columnCount = when (viewType) {
                FileViewType.LIST -> 1
                FileViewType.GRID -> (maxWidth / GRID_COLUMN_MIN_WIDTH).toInt().coerceAtLeast(2)
            }
            val contentPadding = PaddingValues(
                top = topPadding,
                bottom = bottomPadding + dimensionResource(R.dimen.list_bottom_padding_with_fab)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusRestorer()
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when {
                            it.key == Key.DirectionLeft && focusedIndex != -1 &&
                                focusedIndex % columnCount == 0 -> onLeftEdgeReached()
                            it.key == Key.DirectionUp && focusedIndex != -1 &&
                                focusedIndex < columnCount -> onTopEdgeReached()
                            it.key == Key.DirectionRight && focusedIndex == -1 &&
                                focusedWithinIndex != -1 &&
                                focusedWithinIndex % columnCount == columnCount - 1 ->
                                onRightEdgeReached()
                            else -> return@onPreviewKeyEvent false
                        }
                        true
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
                    val itemAppearance = remember(appearance) {
                        appearance.takeIf { it.startItem(index) }
                    }
                    // Remembered so that items whose data didn't change can skip recomposition.
                    val itemModifier = remember(itemAppearance, index) {
                        Modifier
                            .itemLayer(itemAppearance, index)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusedIndex = index
                                } else if (focusedIndex == index) {
                                    focusedIndex = -1
                                }
                                if (it.hasFocus) {
                                    focusedWithinIndex = index
                                } else if (focusedWithinIndex == index) {
                                    focusedWithinIndex = -1
                                }
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
                                description = if (file.attributes.isDirectory) {
                                    null
                                } else {
                                    descriptions.getOrPut(file) { file.formatDescription(context) }
                                },
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
    // Not observed: the items only read it when they're first composed.
    private var isStarting = isEnabled

    // A single clock for all the items, instead of an animation each, in milliseconds since the
    // first one started to appear.
    private val time = Animatable(0f)

    // Set when scrolling, which shows all the items at once. Unlike snapping the clock to its end,
    // it can't be lost when the animation is canceled at the same time.
    private var isEnded by mutableStateOf(false)

    // The first visible item appears first, and the last one composed while starting appears last.
    private var firstIndex = 0
    private var lastIndex = 0

    /** Whether the item at [index], composed now, appears with the animation. */
    fun startItem(index: Int): Boolean {
        if (isStarting) {
            lastIndex = max(lastIndex, index)
        }
        return isStarting
    }

    suspend fun animate(gridState: LazyGridState) {
        // The items laid out in the first frame are composed once it's over.
        withFrameNanos {}
        if (!isStarting) {
            return
        }
        isStarting = false
        firstIndex = gridState.firstVisibleItemIndex
        val durationMillis = APPEARANCE_DURATION_MILLIS +
            (lastIndex - firstIndex).coerceAtLeast(0) * APPEARANCE_STAGGER_MILLIS
        coroutineScope {
            val endOnScroll = launch {
                snapshotFlow { gridState.isScrollInProgress }.first { it }
                isEnded = true
                this@coroutineScope.cancel()
            }
            time.animateTo(
                durationMillis.toFloat(), tween(durationMillis, easing = LinearEasing)
            )
            endOnScroll.cancel()
        }
    }

    /** How long ago the item at [index] started to appear, up to the duration of its animation. */
    fun getItemTime(index: Int): Float =
        if (isEnded) {
            APPEARANCE_DURATION_MILLIS.toFloat()
        } else {
            (time.value - (index - firstIndex).coerceAtLeast(0) * APPEARANCE_STAGGER_MILLIS)
                .coerceIn(0f, APPEARANCE_DURATION_MILLIS.toFloat())
        }
}

// Each item has a layer of its own, so that scrolling only moves the layers instead of drawing
// their content again, which takes long on slow CPU cores because of the text.
private fun Modifier.itemLayer(appearance: ListAppearance?, index: Int): Modifier =
    graphicsLayer {
        val millis = appearance?.getItemTime(index) ?: return@graphicsLayer
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

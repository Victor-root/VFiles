/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.pdf

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.applicationScope
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.ui.ActionButton
import me.zhanghai.android.files.ui.MediaViewerError
import me.zhanghai.android.files.ui.MediaViewerSystemUiEffect
import me.zhanghai.android.files.ui.MediaViewerTopAppBar
import me.zhanghai.android.files.ui.TextFieldDialog
import me.zhanghai.android.files.ui.queryDisplayName
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.isTelevision
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import kotlin.math.roundToInt

/** Shows a PDF opened from the files or from another app, page by page. */
class PdfViewerActivity : AppActivity() {
    @Composable
    override fun Content() {
        val uri = intent.data
        if (uri == null) {
            LaunchedEffect(Unit) { finish() }
            return
        }
        PdfViewerScreen(uri, intent.extraPath?.fileName?.toString())
    }
}

@Composable
private fun PdfViewerScreen(uri: Uri, fileName: String?) {
    val context = LocalContext.current
    val title by produceState(fileName ?: uri.lastPathSegment.orEmpty(), context, uri) {
        if (fileName == null) {
            context.queryDisplayName(uri)?.let { value = it }
        }
    }
    val documentResult by produceState<Result<PdfDocument>?>(null, context, uri) {
        val result = try {
            Result.success(PdfDocument.open(context, uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
        value = result
        // Once the pages being drawn are done.
        awaitDispose { result.getOrNull()?.let { applicationScope.launch { it.close() } } }
    }
    val document = documentResult?.getOrNull()
    val pageCount = document?.pageCount ?: 0
    // Pages are either fit whole in the screen one at a time, or as wide as the screen one after
    // the other, like at first on a TV whose wide screen leaves a fit page small.
    var isFitWidth by rememberSaveable { mutableStateOf(context.isTelevision) }
    val pagerState = rememberPagerState { pageCount }
    val listState = rememberLazyListState()
    // The page in the middle of the screen when fit to its width.
    val listCurrentPage by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo.lastOrNull { it.offset <= center }?.index
                ?: listState.firstVisibleItemIndex
        }
    }
    val currentPage = if (isFitWidth) listCurrentPage else pagerState.currentPage
    var appBarHeight by remember { mutableIntStateOf(0) }
    var isSystemUiShown by rememberSaveable { mutableStateOf(true) }
    var isGoToPageDialogShown by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var isControlFocused by remember { mutableStateOf(false) }
    val actionsFocusRequester = remember { FocusRequester() }
    MediaViewerSystemUiEffect(isSystemUiShown)
    // On a remote, back from the app bar goes back to the pages.
    BackHandler(enabled = isControlFocused) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // A remote goes through the pages with its arrows, or scrolls them up and down when
            // they are fit to the width, and up from the top reaches the app bar.
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                isControlFocused = it.hasFocus && !it.isFocused
            }
            .onKeyEvent {
                if (!isFocused || it.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                val isAtTop = if (isFitWidth) !listState.canScrollBackward else currentPage == 0
                if (it.key == Key.DirectionUp && isAtTop) {
                    // The app bar is over the pages, so it's out of the reach of the focus search.
                    isSystemUiShown = true
                    coroutineScope.launch {
                        // Lets the app bar appear.
                        withFrameNanos {}
                        actionsFocusRequester.requestFocus()
                    }
                    return@onKeyEvent true
                }
                if (isFitWidth && (it.key == Key.DirectionUp || it.key == Key.DirectionDown)) {
                    val distance = listState.layoutInfo.viewportSize.height * SCROLL_FRACTION
                    coroutineScope.launch {
                        listState.animateScrollBy(
                            if (it.key == Key.DirectionUp) -distance else distance
                        )
                    }
                    return@onKeyEvent true
                }
                val page = when (it.key) {
                    Key.DirectionUp, Key.DirectionLeft, Key.PageUp, Key.ChannelUp ->
                        currentPage - 1
                    Key.DirectionDown, Key.DirectionRight, Key.PageDown, Key.ChannelDown ->
                        currentPage + 1
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        isSystemUiShown = !isSystemUiShown
                        return@onKeyEvent true
                    }
                    else -> return@onKeyEvent false
                }
                if (page !in 0..<pageCount) {
                    return@onKeyEvent false
                }
                coroutineScope.launch {
                    if (isFitWidth) {
                        listState.animateScrollToItem(page)
                    } else {
                        pagerState.animateScrollToPage(page)
                    }
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        val result = documentResult
        // Keeps the top of the pages below the app bar.
        val topPadding = with(LocalDensity.current) { appBarHeight.toDp() }
        when {
            result == null -> CircularProgressIndicator()
            document == null -> {
                val throwable = result.exceptionOrNull()!!
                MediaViewerError(
                    throwable,
                    if (throwable is SecurityException) {
                        stringResource(R.string.pdf_viewer_password_protected)
                    } else {
                        throwable.toString()
                    }
                )
            }
            isFitWidth ->
                PdfWidePages(
                    document = document,
                    listState = listState,
                    topPadding = topPadding,
                    onClick = { isSystemUiShown = !isSystemUiShown }
                )
            else ->
                PdfPages(
                    document = document,
                    pagerState = pagerState,
                    topPadding = topPadding,
                    onClick = { isSystemUiShown = !isSystemUiShown }
                )
        }
        AnimatedVisibility(
            visible = isSystemUiShown,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { appBarHeight = it.height },
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            MediaViewerTopAppBar(
                title = title,
                subtitle = document?.let {
                    stringResource(
                        R.string.pdf_viewer_subtitle_format, currentPage + 1, it.pageCount
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .focusRequester(actionsFocusRequester)
                            .focusProperties { down = focusRequester }
                    ) {
                        if (document != null) {
                            ActionButton(
                                if (isFitWidth) {
                                    R.drawable.fit_page_icon_control_normal_24dp
                                } else {
                                    R.drawable.fit_width_icon_control_normal_24dp
                                },
                                stringResource(
                                    if (isFitWidth) {
                                        R.string.pdf_viewer_fit_page
                                    } else {
                                        R.string.pdf_viewer_fit_width
                                    }
                                )
                            ) {
                                // Stays on the same page.
                                if (isFitWidth) {
                                    pagerState.requestScrollToPage(currentPage)
                                } else {
                                    listState.requestScrollToItem(currentPage)
                                }
                                isFitWidth = !isFitWidth
                            }
                        }
                        if (pageCount > 1) {
                            ActionButton(
                                R.drawable.navigate_to_icon_control_normal_24dp,
                                stringResource(R.string.pdf_viewer_go_to_page)
                            ) { isGoToPageDialogShown = true }
                        }
                        ActionButton(
                            R.drawable.share_icon_control_normal_24dp,
                            stringResource(R.string.share)
                        ) {
                            context.startActivitySafe(
                                uri.createSendStreamIntent(MimeType.PDF).withChooser()
                            )
                        }
                    }
                }
            )
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    if (isGoToPageDialogShown && document != null) {
        val pageError = stringResource(R.string.pdf_viewer_page_error)
        TextFieldDialog(
            title = stringResource(R.string.pdf_viewer_go_to_page),
            onConfirm = { text ->
                val page = text.toInt() - 1
                if (isFitWidth) {
                    listState.requestScrollToItem(page)
                } else {
                    pagerState.requestScrollToPage(page)
                }
            },
            onDismissRequest = { isGoToPageDialogShown = false },
            initialText = (currentPage + 1).toString(),
            label = stringResource(R.string.pdf_viewer_page_hint_format, pageCount),
            validate = { text ->
                pageError.takeIf { text.toIntOrNull().let { it == null || it !in 1..pageCount } }
            },
            keyboardType = KeyboardType.Number
        )
    }
}

@Composable
private fun PdfPages(
    document: PdfDocument,
    pagerState: PagerState,
    topPadding: Dp,
    onClick: () -> Unit
) {
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it }
    ) { page ->
        PdfPageView(document, page, Modifier.padding(top = topPadding), onClick)
    }
}

@Composable
private fun PdfPageView(
    document: PdfDocument,
    index: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var error by remember(document, index) { mutableStateOf<Throwable?>(null) }
    val zoomableImageState = rememberZoomableImageState()
    val request = remember(context, document, index) {
        ImageRequest.Builder(context)
            .data(PdfPage(document, index))
            .listener(
                onError = { _, result ->
                    result.throwable.printStackTrace()
                    error = result.throwable
                }
            )
            .build()
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val currentError = error
        if (currentError != null) {
            MediaViewerError(currentError)
        } else {
            ZoomableAsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                state = zoomableImageState,
                onClick = { onClick() }
            )
            if (!zoomableImageState.isImageDisplayed) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PdfWidePages(
    document: PdfDocument,
    listState: LazyListState,
    topPadding: Dp,
    onClick: () -> Unit
) {
    // Zooming in makes the pages wider than the screen, which then scrolls sideways too.
    var scale by remember { mutableFloatStateOf(1f) }
    val horizontalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Before the list, so that it doesn't scroll while pinching.
            .pointerInput(listState, horizontalScrollState) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } < 2) {
                            continue
                        }
                        val newScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                        val ratio = newScale / scale
                        if (ratio != 1f) {
                            val focus = event.calculateCentroid()
                            listState.keepFocusWhileZooming(focus.y, ratio)
                            val scrollX = (horizontalScrollState.value + focus.x) * ratio - focus.x
                            scale = newScale
                            coroutineScope.launch {
                                // Once the pages are as wide as they are now zoomed.
                                withFrameNanos {}
                                horizontalScrollState.scrollTo(scrollX.roundToInt())
                            }
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .horizontalScroll(horizontalScrollState)
                .requiredWidth(maxWidth * scale)
                .fillMaxHeight(),
            state = listState,
            contentPadding = PaddingValues(top = topPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(document.pageCount, key = { it }) { index ->
                PdfWidePageView(document, index, onClick)
            }
        }
    }
}

/** Keeps the point at [focusY] in the viewport over the same spot of its page, zoomed by [ratio]. */
private fun LazyListState.keepFocusWhileZooming(focusY: Float, ratio: Float) {
    val layoutInfo = layoutInfo
    val item = layoutInfo.visibleItemsInfo.lastOrNull {
        it.offset - layoutInfo.viewportStartOffset <= focusY
    } ?: return
    val itemTop = item.offset - layoutInfo.viewportStartOffset
    val newItemTop = focusY - (focusY - itemTop) * ratio
    requestScrollToItem(item.index, (-layoutInfo.viewportStartOffset - newItemTop).roundToInt())
}

@Composable
private fun PdfWidePageView(document: PdfDocument, index: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    // Most pages are A4, which keeps the list from jumping while the actual size is read.
    val aspectRatio by produceState(A4_ASPECT_RATIO, document, index) {
        value = document.getPageAspectRatio(index)
    }
    val request = remember(context, document, index) {
        ImageRequest.Builder(context)
            .data(PdfPage(document, index))
            // As wide as the fetcher draws by default, so that it stays sharp when zoomed in.
            .size(Size.ORIGINAL)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(Color.White)
            .pointerInput(onClick) { detectTapGestures { onClick() } },
        contentScale = ContentScale.FillWidth
    )
}

// How much of the screen a remote's up and down keys scroll pages fit to its width.
private const val SCROLL_FRACTION = 0.8f

private const val A4_ASPECT_RATIO = 210f / 297f

private const val MAX_SCALE = 4f

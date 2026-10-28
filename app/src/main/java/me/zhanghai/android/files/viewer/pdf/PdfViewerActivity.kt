/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.pdf

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import coil3.request.ImageRequest
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
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser

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
    val pagerState = rememberPagerState { document?.pageCount ?: 0 }
    var isSystemUiShown by rememberSaveable { mutableStateOf(true) }
    var isGoToPageDialogShown by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    MediaViewerSystemUiEffect(isSystemUiShown)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // A remote goes through the pages with its arrows, and up from the first one reaches
            // the app bar.
            .focusRequester(focusRequester)
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                val page = when (it.key) {
                    Key.DirectionUp, Key.DirectionLeft, Key.PageUp, Key.ChannelUp ->
                        pagerState.currentPage - 1
                    Key.DirectionDown, Key.DirectionRight, Key.PageDown, Key.ChannelDown ->
                        pagerState.currentPage + 1
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        isSystemUiShown = !isSystemUiShown
                        return@onKeyEvent true
                    }
                    else -> return@onKeyEvent false
                }
                if (page !in 0..<pagerState.pageCount) {
                    return@onKeyEvent false
                }
                coroutineScope.launch { pagerState.animateScrollToPage(page) }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        val result = documentResult
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
            else -> PdfPages(document, pagerState) { isSystemUiShown = !isSystemUiShown }
        }
        AnimatedVisibility(
            visible = isSystemUiShown,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            MediaViewerTopAppBar(
                title = title,
                subtitle = document?.let {
                    stringResource(
                        R.string.pdf_viewer_subtitle_format, pagerState.currentPage + 1,
                        it.pageCount
                    )
                },
                actions = {
                    if (document != null && document.pageCount > 1) {
                        ActionButton(
                            R.drawable.navigate_to_icon_control_normal_24dp,
                            stringResource(R.string.pdf_viewer_go_to_page)
                        ) { isGoToPageDialogShown = true }
                    }
                    ActionButton(
                        R.drawable.share_icon_control_normal_24dp, stringResource(R.string.share)
                    ) {
                        context.startActivitySafe(
                            uri.createSendStreamIntent(MimeType.PDF).withChooser()
                        )
                    }
                }
            )
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    if (isGoToPageDialogShown && document != null) {
        val pageCount = document.pageCount
        val pageError = stringResource(R.string.pdf_viewer_page_error)
        TextFieldDialog(
            title = stringResource(R.string.pdf_viewer_go_to_page),
            onConfirm = { text ->
                coroutineScope.launch { pagerState.scrollToPage(text.toInt() - 1) }
            },
            onDismissRequest = { isGoToPageDialogShown = false },
            initialText = (pagerState.currentPage + 1).toString(),
            label = stringResource(R.string.pdf_viewer_page_hint_format, pageCount),
            validate = { text ->
                pageError.takeIf { text.toIntOrNull().let { it == null || it !in 1..pageCount } }
            },
            keyboardType = KeyboardType.Number
        )
    }
}

@Composable
private fun PdfPages(document: PdfDocument, pagerState: PagerState, onClick: () -> Unit) {
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it }
    ) { page ->
        PdfPageView(document, page, onClick)
    }
}

@Composable
private fun PdfPageView(document: PdfDocument, index: Int, onClick: () -> Unit) {
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

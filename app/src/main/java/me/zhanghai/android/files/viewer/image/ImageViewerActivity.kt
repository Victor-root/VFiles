/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.image

import android.content.Intent
import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.request.ImageRequest
import java8.nio.file.Path
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.provider.common.delete
import me.zhanghai.android.files.ui.AppTopAppBar
import me.zhanghai.android.files.ui.ConfirmationDialog
import me.zhanghai.android.files.util.ParcelableListParceler
import me.zhanghai.android.files.util.createSendImageIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import java.io.IOException
import kotlin.math.abs

class ImageViewerActivity : AppActivity() {
    override val hasDarkSystemBars: Boolean
        get() = true

    @Composable
    override fun Content() {
        val paths = intent.extraPathList
        if (paths.isEmpty()) {
            LaunchedEffect(Unit) { finish() }
            return
        }
        ImageViewerScreen(paths, intent.getIntExtra(EXTRA_POSITION, 0))
    }

    companion object {
        private val EXTRA_POSITION = "${ImageViewerActivity::class.java.name}.extra.POSITION"

        fun putExtras(intent: Intent, paths: List<Path>, position: Int) {
            // All extra put here must be framework classes, or we may crash the resolver activity.
            intent.extraPathList = paths
            intent.putExtra(EXTRA_POSITION, position)
        }
    }
}

@Parcelize
private class PathList(val paths: @WriteWith<ParcelableListParceler> List<Path>) : Parcelable

private val APP_BAR_COLOR = Color.Black.copy(alpha = 0.5f)

@Composable
private fun ImageViewerScreen(initialPaths: List<Path>, initialPosition: Int) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var pathList by rememberSaveable { mutableStateOf(PathList(initialPaths)) }
    val paths = pathList.paths
    val pagerState = rememberPagerState(initialPage = initialPosition) { paths.size }
    var isSystemUiShown by rememberSaveable { mutableStateOf(true) }
    var pathToDelete by remember { mutableStateOf<Path?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    SystemUiEffect(isSystemUiShown)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // A remote's left and right keys go to the previous and next images.
            .focusRequester(focusRequester)
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                val page = when (it.key) {
                    Key.DirectionLeft -> pagerState.currentPage - 1
                    Key.DirectionRight -> pagerState.currentPage + 1
                    else -> return@onKeyEvent false
                }
                if (page in paths.indices) {
                    coroutineScope.launch { pagerState.animateScrollToPage(page) }
                }
                true
            }
            .focusable()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { paths[it] }
        ) { page ->
            ImageViewerPage(
                path = paths[page],
                // Pages on the right are drawn behind the current one.
                modifier = Modifier
                    .zIndex(-page.toFloat())
                    .depthPageTransition(pagerState, page),
                onClick = { isSystemUiShown = !isSystemUiShown }
            )
        }
        AnimatedVisibility(
            visible = isSystemUiShown,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            val currentPath = paths[pagerState.currentPage.coerceIn(paths.indices)]
            ImageViewerTopAppBar(
                title = currentPath.fileName.toString(),
                subtitle = if (paths.size > 1) {
                    stringResource(
                        R.string.image_viewer_subtitle_format, pagerState.currentPage + 1,
                        paths.size
                    )
                } else {
                    null
                },
                actions = {
                    var isMenuShown by remember { mutableStateOf(false) }
                    IconButton(onClick = { isMenuShown = true }) {
                        Icon(
                            painter = painterResource(R.drawable.more_vertical_icon_white_24dp),
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuShown,
                        onDismissRequest = { isMenuShown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                isMenuShown = false
                                pathToDelete = currentPath
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share)) },
                            onClick = {
                                isMenuShown = false
                                val intent = currentPath.fileProviderUri.createSendImageIntent()
                                    .apply { extraPath = currentPath }
                                    .withChooser()
                                context.startActivitySafe(intent)
                            }
                        )
                    }
                }
            )
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    val path = pathToDelete
    if (path != null) {
        ConfirmationDialog(
            text = stringResource(R.string.image_viewer_delete_message_format, path.fileName),
            onConfirm = {
                try {
                    path.delete()
                } catch (e: IOException) {
                    e.printStackTrace()
                    context.showToast(e.toString())
                    return@ConfirmationDialog
                }
                val remainingPaths = paths - path
                if (remainingPaths.isEmpty()) {
                    activity?.finish()
                } else {
                    pathList = PathList(remainingPaths)
                }
            },
            onDismissRequest = { pathToDelete = null }
        )
    }
}

@Composable
private fun ImageViewerTopAppBar(
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit
) {
    AppTopAppBar(
        title = title,
        subtitle = subtitle,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = APP_BAR_COLOR,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
            subtitleContentColor = Color.White
        )
    )
}

@Composable
private fun SystemUiEffect(isShown: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(isShown) {
        WindowCompat.getInsetsController(activity.window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isShown) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }
}

@Composable
private fun ImageViewerPage(path: Path, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var error by remember(path) { mutableStateOf<Throwable?>(null) }
    val zoomableImageState = rememberZoomableImageState()
    // Images are loaded through the file provider, which lets large ones be sub-sampled.
    val request = remember(context, path) {
        ImageRequest.Builder(context)
            .data(path.fileProviderUri)
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
            ImageViewerError(currentError)
        } else {
            ZoomableAsyncImage(
                model = request,
                contentDescription = path.fileName.toString(),
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
private fun ImageViewerError(throwable: Throwable) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(R.drawable.error_icon_white_240dp),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            tint = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = throwable.toString(),
            modifier = Modifier.padding(16.dp),
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

private const val MIN_SCALE = 0.75f

// The page on the right fades and shrinks behind the current one, like a stack.
private fun Modifier.depthPageTransition(pagerState: PagerState, page: Int): Modifier =
    graphicsLayer {
        val position = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
        when {
            position <= 0f -> {
                alpha = if (position < -1f) 0f else 1f
            }
            position <= 1f -> {
                alpha = 1 - position
                translationX = size.width * -position
                val scale = MIN_SCALE + (1 - MIN_SCALE) * (1 - abs(position))
                scaleX = scale
                scaleY = scale
            }
            else -> alpha = 0f
        }
    }

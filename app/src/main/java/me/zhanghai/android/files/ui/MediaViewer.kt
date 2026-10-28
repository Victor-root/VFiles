/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.theme.DarkSystemBarsEffect
import me.zhanghai.android.files.theme.LocalAppColors
import kotlin.math.abs

// The pieces shared by the full screen viewers of photos and videos.

/** An app bar over the media, opaque in the color of the app bar of the other screens. */
@Composable
fun MediaViewerTopAppBar(
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit
) {
    val appColors = LocalAppColors.current
    AppTopAppBar(
        title = title,
        subtitle = subtitle,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = appColors.appBarContainer,
            navigationIconContentColor = appColors.onAppBar,
            titleContentColor = appColors.onAppBar,
            actionIconContentColor = appColors.onAppBar,
            subtitleContentColor = appColors.onAppBar
        )
    )
}

/**
 * Shows or hides the system bars, which can still be shown with a swipe while hidden, and makes
 * their icons light over the dark media. Once the viewer isn't shown anymore, like when another
 * screen is opened over it, the system bars are shown again.
 */
@Composable
fun MediaViewerSystemUiEffect(isShown: Boolean) {
    DarkSystemBarsEffect()
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    LifecycleStartEffect(isShown) {
        val insetsController = WindowCompat.getInsetsController(activity.window, view)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isShown) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onStopOrDispose {
            if (!isShown) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun MediaViewerError(throwable: Throwable, message: String = throwable.toString()) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(R.drawable.error_icon_white_240dp),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            tint = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

private const val MIN_SCALE = 0.75f

/**
 * The page on the right fades and shrinks behind the current one, like a stack. The pages on the
 * right need to be drawn behind the current one.
 */
fun Modifier.depthPageTransition(pagerState: PagerState, page: Int): Modifier =
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

/** The name of the file at [uri] to show, null when it can't be read. */
suspend fun Context.queryDisplayName(uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }
            .onFailure { it.printStackTrace() }
            .getOrNull()
    }

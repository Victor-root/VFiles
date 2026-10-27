/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

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
import androidx.compose.runtime.DisposableEffect
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
import me.zhanghai.android.files.R
import kotlin.math.abs

// The pieces shared by the full screen viewers of photos and videos.

private val APP_BAR_COLOR = Color.Black.copy(alpha = 0.5f)

/** An app bar over the media, translucent with white content unless given other colors. */
@Composable
fun MediaViewerTopAppBar(
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    containerColor: Color = APP_BAR_COLOR,
    contentColor: Color = Color.White
) {
    AppTopAppBar(
        title = title,
        subtitle = subtitle,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            navigationIconContentColor = contentColor,
            titleContentColor = contentColor,
            actionIconContentColor = contentColor,
            subtitleContentColor = contentColor
        )
    )
}

/** Shows or hides the system bars, which can still be shown with a swipe while hidden. */
@Composable
fun MediaViewerSystemUiEffect(isShown: Boolean) {
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
fun MediaViewerError(throwable: Throwable) {
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

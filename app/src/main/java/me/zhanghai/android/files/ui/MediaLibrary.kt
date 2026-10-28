/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import me.zhanghai.android.files.R
import me.zhanghai.android.files.theme.LocalAppColors
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.supportsExternalStorageManager

// The pieces shared by the libraries of media, like the gallery and the music.

private const val FOCUSED_SCALE = 1.08f
private val FOCUSED_BORDER_WIDTH = 3.dp

/** The scale of an item growing while focused, which stands out more than an overlay on photos. */
@Composable
fun animateFocusedScale(isFocused: Boolean): State<Float> =
    animateFloatAsState(if (isFocused) FOCUSED_SCALE else 1f)

/** Draws a focused item grown by [scale] above its neighbors. */
fun Modifier.focusedScale(isFocused: Boolean, scale: () -> Float): Modifier =
    zIndex(if (isFocused) 1f else 0f)
        .graphicsLayer {
            val currentScale = scale()
            scaleX = currentScale
            scaleY = currentScale
        }

/** Outlines a focused item in the accent color. */
fun Modifier.focusedBorder(isFocused: Boolean, color: Color, shape: Shape): Modifier =
    if (isFocused) border(FOCUSED_BORDER_WIDTH, color, shape) else this

private val ALBUM_SHAPE = RoundedCornerShape(16.dp)

/**
 * An album with its square [cover] loaded by Coil, or else [placeholderIconRes], its [name] and
 * [subtitle]. On a D-pad, it's focused when [isToFocus], like when coming back from it, after which
 * [onFocused] is called.
 */
@Composable
fun AlbumCard(
    cover: Any?,
    @DrawableRes placeholderIconRes: Int,
    name: String,
    subtitle: String,
    isToFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    if (isToFocus) {
        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
            onFocused()
        }
    }
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFocusedScale(isFocused)
    Column(
        modifier = Modifier
            .focusedScale(isFocused) { scale }
            .clip(ALBUM_SHAPE)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Cover(
            cover = cover,
            placeholderIconRes = placeholderIconRes,
            modifier = Modifier
                .fillMaxWidth()
                .clip(ALBUM_SHAPE)
                .focusedBorder(isFocused, MaterialTheme.colorScheme.primary, ALBUM_SHAPE)
        )
        Text(
            text = name,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = subtitle,
            modifier = Modifier.padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * A square [cover] loaded by Coil, over [placeholderIconRes] which is seen while it loads or when
 * there's none, like for songs without one.
 */
@Composable
fun Cover(cover: Any?, @DrawableRes placeholderIconRes: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(placeholderIconRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.4f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AsyncImage(
            model = cover,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/** Tells there's nothing in a library, or else why, with [content] below. */
@Composable
fun MediaLibraryMessage(
    @DrawableRes iconRes: Int,
    text: String,
    content: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        content()
    }
}

/**
 * Asks for the access to the media with [text], which is given in the settings, after which
 * [onReload] reads them again.
 */
@Composable
fun MediaLibraryAccessDenied(@DrawableRes iconRes: Int, text: String, onReload: () -> Unit) {
    val context = LocalContext.current
    // Coming back from the settings where the access is given.
    LifecycleResumeEffect(Unit) {
        onReload()
        onPauseOrDispose {}
    }
    MediaLibraryMessage(iconRes, text) {
        Button(
            onClick = { context.startActivitySafe(context.createAccessSettingsIntent()) },
            modifier = Modifier.padding(top = 16.dp)
        ) { Text(stringResource(R.string.allow_access)) }
    }
}

private fun Context.createAccessSettingsIntent(): Intent =
    if (Environment::class.supportsExternalStorageManager()) {
        Environment::class.createManageAppAllFilesAccessPermissionIntent(packageName)
    } else {
        Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    }

/**
 * A switch between [tabs] in the app bar, which leaves the whole height to the media unlike a
 * bottom bar.
 */
@Composable
fun <T> AppBarTabSwitch(
    tabs: List<T>,
    selectedTab: T,
    onTabChange: (T) -> Unit,
    iconRes: (T) -> Int,
    label: @Composable (T) -> String
) {
    val appColors = LocalAppColors.current
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = appColors.onAppBar,
        activeContentColor = appColors.appBarContainer,
        activeBorderColor = appColors.onAppBar,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = appColors.onAppBar,
        inactiveBorderColor = appColors.onAppBar
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 12.dp)) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selectedTab,
                onClick = { onTabChange(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                colors = colors,
                icon = {},
                label = {
                    Icon(
                        painter = painterResource(iconRes(tab)),
                        contentDescription = label(tab),
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

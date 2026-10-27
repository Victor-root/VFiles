/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.zhanghai.android.files.R
import me.zhanghai.android.files.coil.AppIconPackageName
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isEncrypted
import me.zhanghai.android.files.ui.MenuDivider
import me.zhanghai.android.files.ui.MenuItem
import me.zhanghai.android.files.ui.RoundedDropdownMenu
import me.zhanghai.android.files.ui.cachedIconPainter

/** What the menu of a file can do. */
interface FileItemActions {
    fun openFileWith(file: FileItem)
    fun cutFile(file: FileItem)
    fun copyFile(file: FileItem)
    fun confirmDeleteFile(file: FileItem)
    fun showRenameFileDialog(file: FileItem)
    fun extractFile(file: FileItem)
    fun showCreateArchiveDialog(file: FileItem)
    fun shareFile(file: FileItem)
    fun copyPath(file: FileItem)
    fun addBookmark(file: FileItem)
    fun createShortcut(file: FileItem)
    fun showPropertiesDialog(file: FileItem)
}

private const val SELECTED_ALPHA = 0.12f
private val GRID_ITEM_SHAPE = RoundedCornerShape(12.dp)
private val GRID_THUMBNAIL_SHAPE = RoundedCornerShape(8.dp)
private const val DISABLED_ALPHA = 0.38f
private const val STROKE_ALPHA = 0.12f
private val ICON_SIZE = 24.dp
private val MENU_BUTTON_SIZE = 40.dp

private val MENU_BUTTON_MARGIN_START =
    R.dimen.content_start_from_screen_edge_margin_minus_36dp_considering_at_least_8dp

/**
 * A file in the list view. On a D-pad, the row is focused as a whole and right moves to its menu
 * button, from which left, up and down come back to the row.
 */
@Composable
fun FileListItem(
    file: FileItem,
    isSelected: Boolean,
    isEnabled: Boolean,
    isPicking: Boolean,
    nameEllipsize: TextUtils.TruncateAt,
    description: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    actions: FileItemActions,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember { FileItemFocusRequesters() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(selectedBackgroundColor(isSelected))
            .focusRequester(focusRequesters.item)
            .focusProperties { right = focusRequesters.menu }
            .combinedClickable(enabled = isEnabled, onClick = onClick, onLongClick = onLongClick)
            .disabledAlpha(isEnabled),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileItemIcon(
            file = file,
            modifier = Modifier.padding(
                start = dimensionResource(R.dimen.screen_edge_margin_minus_4dp),
                end = dimensionResource(R.dimen.content_start_from_screen_edge_margin_minus_44dp)
            ),
            isSelected = isSelected,
            onClick = onIconClick
        )
        Column(modifier = Modifier.weight(1f)) {
            FileName(
                name = file.name,
                ellipsize = nameEllipsize,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description != null) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        FileItemMenuButton(
            file = file,
            isEnabled = isEnabled,
            isPicking = isPicking,
            actions = actions,
            focusRequesters = focusRequesters,
            modifier = Modifier.padding(
                start = dimensionResource(MENU_BUTTON_MARGIN_START),
                end = dimensionResource(R.dimen.screen_edge_margin_minus_12dp_at_least_8dp)
            )
        )
    }
}

/** A file in the grid view, with a large thumbnail above its name. */
@Composable
fun FileGridItem(
    file: FileItem,
    isSelected: Boolean,
    isEnabled: Boolean,
    isPicking: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    actions: FileItemActions,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember { FileItemFocusRequesters() }
    val shape = GRID_ITEM_SHAPE
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(shape)
            .background(selectedBackgroundColor(isSelected), shape)
            .focusRequester(focusRequesters.item)
            .focusProperties { right = focusRequesters.menu }
            .combinedClickable(enabled = isEnabled, onClick = onClick, onLongClick = onLongClick)
            .disabledAlpha(isEnabled)
    ) {
        // The margins are from the edges of the cell, outside the 4dp padding above.
        val thumbnailMargin = dimensionResource(R.dimen.screen_edge_margin) - 4.dp
        FileGridThumbnail(
            file = file,
            modifier = Modifier.padding(
                start = thumbnailMargin, top = thumbnailMargin, end = thumbnailMargin
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensionResource(R.dimen.screen_edge_margin_minus_8dp) - 4.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconMargin = dimensionResource(R.dimen.screen_edge_margin_minus_12dp)
            FileItemIcon(
                file = file,
                modifier = Modifier.padding(start = iconMargin - 4.dp),
                isSelected = isSelected,
                showsThumbnail = false,
                onClick = onIconClick
            )
            Text(
                text = file.name,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            FileItemMenuButton(
                file = file,
                isEnabled = isEnabled,
                isPicking = isPicking,
                actions = actions,
                focusRequesters = focusRequesters,
                modifier = Modifier.padding(end = iconMargin - 4.dp)
            )
        }
    }
}

// Only disabled items get a layer for their alpha.
private fun Modifier.disabledAlpha(isEnabled: Boolean): Modifier =
    if (isEnabled) this else alpha(DISABLED_ALPHA)

@Composable
private fun selectedBackgroundColor(isSelected: Boolean): Color =
    if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ALPHA)
    } else {
        Color.Transparent
    }

private class FileItemFocusRequesters {
    val item = FocusRequester()
    val menu = FocusRequester()
}

@Composable
private fun FileName(name: String, ellipsize: TextUtils.TruncateAt, style: TextStyle) {
    if (ellipsize == TextUtils.TruncateAt.MARQUEE) {
        Text(
            text = name,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = style
        )
    } else {
        Text(
            text = name,
            maxLines = 1,
            overflow = when (ellipsize) {
                TextUtils.TruncateAt.START -> TextOverflow.StartEllipsis
                TextUtils.TruncateAt.MIDDLE -> TextOverflow.MiddleEllipsis
                else -> TextOverflow.Ellipsis
            },
            style = style
        )
    }
}

/** The last modification time and the size of a file. */
fun FileItem.formatDescription(context: Context): String {
    val lastModificationTime = attributes.lastModifiedTime().toInstant().formatShort(context)
    val size = attributes.fileSize.formatHumanReadable(context)
    return listOf(lastModificationTime, size)
        .joinToString(context.getString(R.string.file_item_description_separator))
}

/**
 * The type icon of a file, or its thumbnail, with its badges. When touched, it calls [onClick] if
 * any, but it isn't on the D-pad focus path: files are selected with a long press there.
 */
@Composable
fun FileItemIcon(
    file: FileItem,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    showsThumbnail: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    // Read when drawing only, so that a loaded thumbnail doesn't recompose the item.
    var isThumbnailShown by remember(file) { mutableStateOf(false) }
    val iconPainter = cachedIconPainter(file.mimeType.iconRes, ICON_SIZE)
    Box(
        modifier = modifier
            .size(48.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .focusProperties { canFocus = false }
                        .clickable(interactionSource = null, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(4.dp)
            // Drawn by the box rather than by an image of its own, since there's one per row.
            .drawBehind {
                if (!isThumbnailShown) {
                    val iconSize = ICON_SIZE.toPx()
                    val inset = (size.width - iconSize) / 2
                    translate(inset, inset) { with(iconPainter) { draw(Size(iconSize, iconSize)) } }
                }
            }
    ) {
        val supportsThumbnail = remember(file) { file.supportsThumbnail }
        if (showsThumbnail && supportsThumbnail) {
            AsyncImage(
                model = file.path to file.attributes,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { isThumbnailShown = true }
            )
        }
        FileItemBadges(file, isSelected)
    }
}

@Composable
private fun BoxScope.FileItemBadges(file: FileItem, isSelected: Boolean) {
    val appDirectoryPackageName = remember(file) { file.appDirectoryPackageName }
    if (appDirectoryPackageName != null) {
        AsyncImage(
            model = AppIconPackageName(appDirectoryPackageName),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(0.5.dp, 0.5.dp)
                .size(19.dp)
        )
    }
    val badgeIconRes = fileBadgeIconRes(file)
    if (badgeIconRes != null) {
        Image(
            painter = cachedIconPainter(badgeIconRes, 18.dp),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
    // Only composed while it's shown or animating, like DropdownMenu does, since most items are
    // never selected.
    val selectedState = remember { MutableTransitionState(isSelected) }
    selectedState.targetState = isSelected
    if (!selectedState.currentState && !selectedState.targetState && selectedState.isIdle) {
        return
    }
    AnimatedVisibility(
        visibleState = selectedState,
        modifier = Modifier.align(Alignment.BottomEnd),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = cachedIconPainter(R.drawable.check_icon_white_24dp, 14.dp),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@DrawableRes
private fun fileBadgeIconRes(file: FileItem): Int? {
    val attributes = file.attributesNoFollowLinks
    return when {
        attributes.isSymbolicLink ->
            if (file.isSymbolicLinkBroken) {
                R.drawable.error_badge_icon_18dp
            } else {
                R.drawable.symbolic_link_badge_icon_18dp
            }
        attributes.isEncrypted() -> R.drawable.encrypted_badge_icon_18dp
        else -> null
    }
}

// A folder outline for directories, otherwise the thumbnail of the file, or its type icon.
@Composable
private fun FileGridThumbnail(file: FileItem, modifier: Modifier = Modifier) {
    val shape = GRID_THUMBNAIL_SHAPE
    val isDirectory = file.attributes.isDirectory
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.78f)
            .then(
                if (isDirectory) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = STROKE_ALPHA), shape
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isDirectory) {
            Icon(
                painter = painterResource(R.drawable.file_directory_thumbnail),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Box
        }
        val iconPainter = cachedIconPainter(file.mimeType.iconRes, 40.dp)
        val supportsThumbnail = remember(file) { file.supportsThumbnail }
        // The thumbnail of an APK is its app icon, shown in place of the type icon.
        if (supportsThumbnail && file.mimeType.isApk) {
            AsyncImage(
                model = file.path to file.attributes,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                placeholder = iconPainter,
                error = iconPainter
            )
            return@Box
        }
        var isThumbnailShown by remember(file) { mutableStateOf(false) }
        Image(
            painter = iconPainter,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .drawWithContent {
                    if (!isThumbnailShown) {
                        drawContent()
                    }
                }
        )
        if (supportsThumbnail) {
            AsyncImage(
                model = file.path to file.attributes,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop,
                onSuccess = { isThumbnailShown = true }
            )
        }
    }
}

@Composable
private fun FileItemMenuButton(
    file: FileItem,
    isEnabled: Boolean,
    isPicking: Boolean,
    actions: FileItemActions,
    focusRequesters: FileItemFocusRequesters,
    modifier: Modifier = Modifier
) {
    var isMenuShown by remember { mutableStateOf(false) }
    // Composing the menu of every item costs much while scrolling, so it's only composed once
    // opened, and then kept for its closing animation.
    var hasMenuBeenShown by remember { mutableStateOf(false) }
    val iconPainter = cachedIconPainter(R.drawable.more_vertical_icon_white_24dp, ICON_SIZE)
    val iconColorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
    val contentDescription = stringResource(R.string.more_options)
    Box(modifier = modifier) {
        // Built like an IconButton with an Icon, with fewer nodes since there's one per row.
        Box(
            modifier = Modifier
                .focusRequester(focusRequesters.menu)
                .focusProperties {
                    left = focusRequesters.item
                    up = focusRequesters.item
                    down = focusRequesters.item
                }
                .minimumInteractiveComponentSize()
                .size(MENU_BUTTON_SIZE)
                .clip(CircleShape)
                .clickable(
                    enabled = isEnabled,
                    role = Role.Button,
                    interactionSource = null,
                    indication = ripple()
                ) {
                    hasMenuBeenShown = true
                    isMenuShown = true
                }
                .semantics { this.contentDescription = contentDescription }
                .paint(
                    iconPainter,
                    sizeToIntrinsics = false,
                    contentScale = ContentScale.Inside,
                    colorFilter = iconColorFilter
                )
        )
        if (hasMenuBeenShown) {
            RoundedDropdownMenu(
                expanded = isMenuShown,
                onDismissRequest = { isMenuShown = false }
            ) { FileItemMenuItems(file, isPicking, actions) { isMenuShown = false } }
        }
    }
}

@Composable
private fun FileItemMenuItems(
    file: FileItem,
    isPicking: Boolean,
    actions: FileItemActions,
    dismiss: () -> Unit
) {
    @Composable
    fun Item(@DrawableRes iconRes: Int, title: String, onClick: (FileItem) -> Unit) {
        MenuItem(iconRes, title) {
            dismiss()
            onClick(file)
        }
    }

    val isReadOnly = file.path.fileSystem.isReadOnly
    val isArchivePath = file.path.isArchivePath
    Item(
        R.drawable.open_with_icon_control_normal_24dp,
        stringResource(R.string.file_item_action_open_with),
        actions::openFileWith
    )
    if (!isPicking || !isReadOnly) {
        MenuDivider()
    }
    if (!isPicking && !isReadOnly) {
        Item(
            R.drawable.cut_icon_control_normal_24dp, stringResource(R.string.cut), actions::cutFile
        )
    }
    if (!isPicking) {
        Item(
            R.drawable.copy_icon_control_normal_24dp,
            stringResource(if (isArchivePath) R.string.file_item_action_extract else R.string.copy),
            actions::copyFile
        )
    }
    if (!isReadOnly) {
        Item(
            R.drawable.rename_icon_control_normal_24dp, stringResource(R.string.rename),
            actions::showRenameFileDialog
        )
        Item(
            R.drawable.delete_icon_control_normal_24dp, stringResource(R.string.delete),
            actions::confirmDeleteFile
        )
    }
    if (file.isArchiveFile || !isArchivePath) {
        MenuDivider()
    }
    if (file.isArchiveFile) {
        Item(
            R.drawable.extract_icon_control_normal_24dp,
            stringResource(R.string.file_item_action_extract), actions::extractFile
        )
    }
    if (!isArchivePath) {
        Item(
            R.drawable.archive_icon_control_normal_24dp,
            stringResource(R.string.file_item_action_archive), actions::showCreateArchiveDialog
        )
    }
    MenuDivider()
    Item(
        R.drawable.share_icon_control_normal_24dp, stringResource(R.string.share),
        actions::shareFile
    )
    Item(
        R.drawable.copy_path_icon_control_normal_24dp,
        stringResource(R.string.file_item_action_copy_path), actions::copyPath
    )
    MenuDivider()
    if (file.attributes.isDirectory) {
        Item(
            R.drawable.bookmark_icon_control_normal_24dp,
            stringResource(R.string.file_item_action_add_bookmark), actions::addBookmark
        )
    }
    Item(
        R.drawable.shortcut_icon_control_normal_24dp,
        stringResource(R.string.file_item_action_create_shortcut), actions::createShortcut
    )
    MenuDivider()
    Item(
        R.drawable.information_icon_control_normal_24dp,
        stringResource(R.string.file_item_action_properties), actions::showPropertiesDialog
    )
}

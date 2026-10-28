/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

@ConsistentCopyVisibility
data class StandardDirectory internal constructor(
    @param:DrawableRes val iconRes: Int,
    val iconColor: NavigationIconColor?,
    @param:StringRes private val titleRes: Int,
    private val customTitle: String?,
    val relativePath: String,
    val isEnabled: Boolean
) {
    constructor(
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        relativePath: String,
        enabled: Boolean,
        iconColor: NavigationIconColor? = null
    ) : this(iconRes, iconColor, titleRes, null, relativePath, enabled)

    val id: Long
        get() = relativePath.hashCode().toLong()

    val key: String
        get() = relativePath

    /** Whether this is the gallery, which can replace the pictures directory in the drawer. */
    val isGallery: Boolean
        get() = relativePath == GALLERY_KEY

    /** Whether this is the music, which can replace the music directory in the drawer. */
    val isMusic: Boolean
        get() = relativePath == MUSIC_KEY

    fun getTitle(context: Context): String =
        if (!customTitle.isNullOrEmpty()) customTitle else context.getString(titleRes)

    fun withSettings(settings: StandardDirectorySettings): StandardDirectory =
        StandardDirectory(
            iconRes, iconColor, titleRes, settings.customTitle, relativePath, settings.isEnabled
        )

    fun toSettings(): StandardDirectorySettings =
        StandardDirectorySettings(relativePath, customTitle, isEnabled)

    companion object {
        // Not directories, but the keys of the gallery and the music among them.
        const val GALLERY_KEY = "gallery"
        const val MUSIC_KEY = "music"
    }
}

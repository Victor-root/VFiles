/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.content.Context
import androidx.annotation.DrawableRes
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.provider.webdav.client.Authentication
import me.zhanghai.android.files.provider.webdav.client.Authority
import me.zhanghai.android.files.provider.webdav.createWebDavRootPath
import kotlin.random.Random

@Parcelize
class WebDavServer(
    override val id: Long,
    override val customName: String?,
    val authority: Authority,
    val authentication: Authentication,
    val relativePath: String
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        authority: Authority,
        authentication: Authentication,
        relativePath: String
    ) : this(id ?: Random.nextLong(), customName, authority, authentication, relativePath)

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.computer_icon_white_24dp

    override fun getDefaultName(context: Context): String =
        if (relativePath.isNotEmpty()) "$authority/$relativePath" else authority.toString()

    override val description: String
        get() = authority.toString()

    override val path: Path
        get() = authority.createWebDavRootPath().resolve(relativePath)

    override fun createEditDestination(): Destination = EditWebDavServerDestination(this)
}

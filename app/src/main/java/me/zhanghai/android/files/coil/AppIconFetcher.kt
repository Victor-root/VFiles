/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import me.zhanghai.android.appiconloader.AppIconLoader
import java.io.Closeable

class AppIconFetcher(
    private val options: Options,
    private val appIconLoader: AppIconLoader,
    private val getApplicationInfo: () -> Pair<ApplicationInfo, Closeable?>
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val (applicationInfo, closeable) = getApplicationInfo()
        val icon = closeable.use { appIconLoader.loadIcon(applicationInfo) }
        // Not sampled because we only load with one fixed size.
        return ImageFetchResult(
            icon.toDrawable(options.context.resources).asImage(), false, DataSource.DISK
        )
    }

    abstract class Factory<T : Any>(
        iconSize: Int,
        context: Context,
        shrinkNonAdaptiveIcons: Boolean = false
    ) : Fetcher.Factory<T> {
        private val appIconLoader =
            AppIconLoader(iconSize, shrinkNonAdaptiveIcons, context)

        override fun create(data: T, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(options, appIconLoader) { getApplicationInfo(data) }

        abstract fun getApplicationInfo(data: T): Pair<ApplicationInfo, Closeable?>
    }
}

/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.coil

import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.svg.SvgDecoder
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.gallery.GalleryMediaKeyer
import me.zhanghai.android.files.gallery.GalleryThumbnailFetcher
import me.zhanghai.android.files.music.MusicArtworkFetcher
import me.zhanghai.android.files.music.MusicTrackKeyer
import me.zhanghai.android.files.viewer.pdf.PdfPageFetcher
import me.zhanghai.android.files.viewer.pdf.PdfPageKeyer

fun initializeCoil() {
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(application)
            .components {
                add(AppIconApplicationInfoKeyer())
                add(AppIconApplicationInfoFetcherFactory(application))
                add(AppIconPackageNameKeyer())
                add(AppIconPackageNameFetcherFactory(application))
                add(PathAttributesKeyer())
                add(PathAttributesFetcher.Factory(application))
                add(GalleryMediaKeyer())
                add(GalleryThumbnailFetcher.Factory(application))
                add(MusicTrackKeyer())
                add(MusicArtworkFetcher.Factory(application))
                add(PdfPageKeyer())
                add(PdfPageFetcher.Factory(application))
                add(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        AnimatedImageDecoder.Factory()
                    } else {
                        GifDecoder.Factory()
                    }
                )
                add(SvgDecoder.Factory(useViewBoundsAsIntrinsicSize = false))
            }
            .build()
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.pdf

import android.content.Context
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/** A page of a PDF to show with Coil, like zoomable. */
class PdfPage(val document: PdfDocument, val index: Int)

class PdfPageKeyer : Keyer<PdfPage> {
    override fun key(data: PdfPage, options: Options): String =
        "${data.document.uri}#${data.index}:${options.size.width}"
}

/**
 * Draws a page of a PDF as wide as asked, or else twice as wide as the screen so that it stays
 * sharp when zoomed in.
 */
class PdfPageFetcher(
    private val data: PdfPage,
    private val options: Options,
    private val context: Context
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val defaultWidth = (context.resources.displayMetrics.widthPixels * 2)
            .coerceAtMost(MAX_WIDTH)
        val width = options.size.width.pxOrElse { defaultWidth }.coerceAtMost(MAX_WIDTH)
        val bitmap = data.document.renderPage(data.index, width)
        return ImageFetchResult(
            bitmap.toDrawable(context.resources).asImage(), false, DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<PdfPage> {
        override fun create(data: PdfPage, options: Options, imageLoader: ImageLoader): Fetcher =
            PdfPageFetcher(data, options, context)
    }

    companion object {
        // Keeps a page within a few tens of megabytes.
        private const val MAX_WIDTH = 2048
    }
}

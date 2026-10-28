/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * A PDF file at [uri] whose pages are drawn one at a time, since the renderer of the platform can
 * only have one page open.
 */
class PdfDocument private constructor(
    val uri: Uri,
    private val fileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    private val mutex = Mutex()

    private var isClosed = false

    val pageCount: Int = renderer.pageCount

    /** Draws the page at [index] [width] pixels wide, on white like paper. */
    suspend fun renderPage(index: Int, width: Int): Bitmap =
        mutex.withLock {
            check(!isClosed) { "$uri is closed" }
            withContext(Dispatchers.IO) {
                renderer.openPage(index).use { page ->
                    val height = (width.toLong() * page.height / page.width).toInt()
                    createBitmap(width, height).apply {
                        eraseColor(Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    /** Closes it once the page being drawn is done. */
    suspend fun close() {
        mutex.withLock {
            isClosed = true
            withContext(Dispatchers.IO) {
                renderer.close()
                fileDescriptor.close()
            }
        }
    }

    companion object {
        /**
         * Opens the PDF at [uri], which throws a [SecurityException] when it's protected by a
         * password.
         */
        suspend fun open(context: Context, uri: Uri): PdfDocument =
            withContext(Dispatchers.IO) {
                val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw FileNotFoundException(uri.toString())
                try {
                    PdfDocument(uri, fileDescriptor, PdfRenderer(fileDescriptor))
                } catch (t: Throwable) {
                    fileDescriptor.close()
                    throw t
                }
            }
    }
}

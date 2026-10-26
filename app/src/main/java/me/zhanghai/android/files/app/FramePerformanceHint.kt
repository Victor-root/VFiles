/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PerformanceHintManager
import android.os.Process
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import me.zhanghai.android.files.util.PerfLog
import java.io.File

/**
 * Reports how long the frames of [window] take to the system, so that it keeps the CPU fast enough
 * while the app animates. Some devices otherwise only speed it up during touches and the flings of
 * View-based lists, and let Compose flings and animations run on slowed down cores.
 */
@RequiresApi(Build.VERSION_CODES.S)
class FramePerformanceHint(private val window: Window) {
    private val mainThreadId = Process.myTid()

    private var session: PerformanceHintManager.Session? = null

    private val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
        val session = session ?: createSession() ?: return@OnFrameMetricsAvailableListener
        // The delay before the frame started isn't work of the frame.
        val workNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION) -
            frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
        if (workNanos > 0) {
            session.reportActualWorkDuration(workNanos)
        }
    }

    init {
        window.addOnFrameMetricsAvailableListener(listener, handler)
    }

    // The render thread exists once frames are drawn, and the system has no API to get it.
    private fun createSession(): PerformanceHintManager.Session? {
        val renderThreadId = File("/proc/self/task").listFiles()
            ?.firstOrNull {
                try {
                    File(it, "comm").readText().trim() == RENDER_THREAD_NAME
                } catch (e: Exception) {
                    false
                }
            }
            ?.name?.toIntOrNull()
        val threadIds = listOfNotNull(mainThreadId, renderThreadId).toIntArray()
        @Suppress("DEPRECATION")
        val refreshRate = window.windowManager.defaultDisplay.refreshRate
        val targetNanos = (NANOS_PER_SECOND / refreshRate).toLong()
        val performanceHintManager =
            window.context.getSystemService(PerformanceHintManager::class.java) ?: return null
        return performanceHintManager.createHintSession(threadIds, targetNanos)
            ?.also { session = it }
            .also { PerfLog.log("hint session=$it threads=${threadIds.toList()}") }
    }

    fun close() {
        window.removeOnFrameMetricsAvailableListener(listener)
        handler.post { session?.close() }
    }

    companion object {
        private const val RENDER_THREAD_NAME = "RenderThread"
        private const val NANOS_PER_SECOND = 1_000_000_000

        private val handler by lazy {
            Handler(HandlerThread("FramePerformanceHint").apply { start() }.looper)
        }
    }
}

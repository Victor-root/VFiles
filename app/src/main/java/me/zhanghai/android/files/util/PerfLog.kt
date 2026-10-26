/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import me.zhanghai.android.files.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Logs frame timings and recomposition counts once per second while frames are drawn, in debug
 * builds only. Filter logcat with the VFilesPerf tag.
 */
object PerfLog {
    private const val TAG = "VFilesPerf"
    private const val NANOS_PER_MILLI = 1_000_000.0

    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    private val handler by lazy {
        Handler(HandlerThread(TAG).apply { start() }.looper)
    }

    private val frames = mutableListOf<LongArray>()
    private var windowStartMillis = 0L
    private var frameBudgetNanos = 0L
    private var display: Display? = null

    /** Counts a composition of the calling scope under [name]. */
    @Composable
    fun Recomposed(name: String) {
        if (BuildConfig.PERF_LOGS) {
            SideEffect { counts.getOrPut(name) { AtomicInteger() }.incrementAndGet() }
        }
    }

    fun start(activity: Activity) {
        if (!BuildConfig.PERF_LOGS) {
            return
        }
        @Suppress("DEPRECATION")
        val display = activity.windowManager.defaultDisplay
        this.display = display
        val refreshRate = display.refreshRate
        frameBudgetNanos = (1_000_000_000 / refreshRate).toLong()
        Log.d(
            TAG, "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "refreshRate=$refreshRate budget=${frameBudgetNanos / NANOS_PER_MILLI}ms " +
                "modes=${display.supportedModes.joinToString { "${it.refreshRate}" }}"
        )
        activity.window.addOnFrameMetricsAvailableListener(::onFrameMetrics, handler)
    }

    private fun onFrameMetrics(window: Window, frameMetrics: FrameMetrics, dropCount: Int) {
        val metrics = longArrayOf(
            frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
            frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
            frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION),
            frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
            frameMetrics.getMetric(FrameMetrics.DRAW_DURATION),
            frameMetrics.getMetric(FrameMetrics.SYNC_DURATION),
            frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
            frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                frameMetrics.getMetric(FrameMetrics.GPU_DURATION)
            } else {
                0
            },
            dropCount.toLong()
        )
        val now = SystemClock.uptimeMillis()
        if (frames.isEmpty()) {
            windowStartMillis = now
        }
        frames += metrics
        if (now - windowStartMillis >= 1000) {
            logWindow(now - windowStartMillis)
            frames.clear()
        }
    }

    private fun logWindow(durationMillis: Long) {
        fun average(index: Int) = frames.sumOf { it[index] } / frames.size / NANOS_PER_MILLI
        val totals = frames.map { it[0] }
        val janky = totals.count { it > frameBudgetNanos }
        Log.d(
            TAG, "refreshRate=${display?.refreshRate} frames=${frames.size} in " +
                "${durationMillis}ms janky=$janky " +
                "dropped=${frames.sumOf { it[9] }} " +
                "total avg=%.1f max=%.1f | input=%.1f anim=%.1f layout=%.1f draw=%.1f sync=%.1f "
                    .format(average(0), totals.max() / NANOS_PER_MILLI, average(1), average(2),
                        average(3), average(4), average(5)) +
                "command=%.1f swap=%.1f gpu=%.1f".format(average(6), average(7), average(8))
        )
        val recompositions = counts.entries.filter { it.value.get() > 0 }
            .sortedBy { it.key }
            .joinToString { "${it.key}=${it.value.getAndSet(0)}" }
        if (recompositions.isNotEmpty()) {
            Log.d(TAG, "recompositions: $recompositions")
        }
    }
}

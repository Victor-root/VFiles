/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.app.Activity
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import me.zhanghai.android.files.BuildConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Logs frame timings, missed frames, slow work and recomposition counts once per second while
 * frames are drawn, in debug builds only. Filter logcat with the VFilesPerf tag.
 */
object PerfLog {
    private const val TAG = "VFilesPerf"
    private const val NANOS_PER_MILLI = 1_000_000.0
    private const val SLOW_WORK_NANOS = 3_000_000L
    // A longer pause between two frames means scrolling stopped, not that frames were missed.
    private const val MAX_GAP_NANOS = 200_000_000L

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val workNanos = ConcurrentHashMap<String, AtomicLong>()

    private val handler by lazy {
        Handler(HandlerThread(TAG).apply { start() }.looper)
    }

    private val frames = mutableListOf<LongArray>()
    private var windowStartMillis = 0L
    private var frameBudgetNanos = 0L
    private var lastVsyncNanos = 0L
    private var missedVsyncs = 0
    private var gcCount = 0L
    private var gcTimeMillis = 0L

    fun log(message: String) {
        if (BuildConfig.PERF_LOGS) {
            Log.d(TAG, message)
        }
    }

    /** Counts a composition of the calling scope under [name]. */
    @Composable
    fun Recomposed(name: String) {
        if (BuildConfig.PERF_LOGS) {
            SideEffect { counts.getOrPut(name) { AtomicInteger() }.incrementAndGet() }
        }
    }

    /** Runs [block], adding its duration to [name] and logging it right away when it's slow. */
    inline fun <T> time(name: String, block: () -> T): T {
        if (!BuildConfig.PERF_LOGS) {
            return block()
        }
        val start = System.nanoTime()
        val result = block()
        addWork(name, System.nanoTime() - start)
        return result
    }

    fun addWork(name: String, nanos: Long) {
        workNanos.getOrPut(name) { AtomicLong() }.addAndGet(nanos)
        if (nanos > SLOW_WORK_NANOS) {
            Log.d(TAG, "slow $name=%.1fms".format(nanos / NANOS_PER_MILLI))
        }
    }

    /** Times the measurement of the modified layout, which composes the new items of a lazy list. */
    fun Modifier.timeMeasure(name: String): Modifier =
        if (BuildConfig.PERF_LOGS) {
            layout { measurable, constraints ->
                val placeable = time(name) { measurable.measure(constraints) }
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        } else {
            this
        }

    fun start(activity: Activity) {
        if (!BuildConfig.PERF_LOGS) {
            return
        }
        @Suppress("DEPRECATION")
        val refreshRate = activity.windowManager.defaultDisplay.refreshRate
        frameBudgetNanos = (1_000_000_000 / refreshRate).toLong()
        Log.d(
            TAG, "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "refreshRate=$refreshRate budget=${frameBudgetNanos / NANOS_PER_MILLI}ms"
        )
        activity.window.addOnFrameMetricsAvailableListener(::onFrameMetrics, handler)
    }

    private fun onFrameMetrics(window: Window, frameMetrics: FrameMetrics, dropCount: Int) {
        val metrics = longArrayOf(
            frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
            frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION),
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
            }
        )
        val vsyncNanos = frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
        val gapNanos = vsyncNanos - lastVsyncNanos
        if (lastVsyncNanos != 0L && gapNanos < MAX_GAP_NANOS) {
            missedVsyncs += ((gapNanos + frameBudgetNanos / 2) / frameBudgetNanos - 1).toInt()
                .coerceAtLeast(0)
        }
        lastVsyncNanos = vsyncNanos
        if (metrics[0] > frameBudgetNanos) {
            Log.d(TAG, "janky frame " + metrics.format() + " cpuMHz=" + readCpuFrequencies())
        }
        val now = SystemClock.uptimeMillis()
        if (frames.isEmpty()) {
            windowStartMillis = now
        }
        frames += metrics
        if (now - windowStartMillis >= 1000) {
            logWindow(now - windowStartMillis)
            frames.clear()
            missedVsyncs = 0
        }
    }

    // The current frequency of each CPU core, to see whether the CPU is slowed down when frames are
    // late.
    private fun readCpuFrequencies(): String =
        (0 until Runtime.getRuntime().availableProcessors()).joinToString("/") {
            try {
                val khz = File("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq")
                    .readText().trim().toLong()
                (khz / 1000).toString()
            } catch (e: Exception) {
                "?"
            }
        }

    private fun LongArray.format(): String =
        ("total=%.1f unknownDelay=%.1f input=%.1f anim=%.1f layout=%.1f draw=%.1f sync=%.1f " +
            "command=%.1f swap=%.1f gpu=%.1f")
            .format(*map { it / NANOS_PER_MILLI }.toTypedArray())

    private fun logWindow(durationMillis: Long) {
        val averages = LongArray(frames.first().size) { index ->
            frames.sumOf { it[index] } / frames.size
        }
        val janky = frames.count { it[0] > frameBudgetNanos }
        val newGcCount = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0
        val newGcTimeMillis = Debug.getRuntimeStat("art.gc.gc-time")?.toLongOrNull() ?: 0
        Log.d(
            TAG, "frames=${frames.size} missed=$missedVsyncs in ${durationMillis}ms " +
                "janky=$janky gc=${newGcCount - gcCount} gcTime=${newGcTimeMillis - gcTimeMillis}ms" +
                " | avg " + averages.format() + " cpuMHz=" + readCpuFrequencies()
        )
        gcCount = newGcCount
        gcTimeMillis = newGcTimeMillis
        val work = workNanos.entries.filter { it.value.get() > 0 }
            .sortedBy { it.key }
            .joinToString { "%s=%.1fms".format(it.key, it.value.getAndSet(0) / NANOS_PER_MILLI) }
        if (work.isNotEmpty()) {
            Log.d(TAG, "work: $work")
        }
        val recompositions = counts.entries.filter { it.value.get() > 0 }
            .sortedBy { it.key }
            .joinToString { "${it.key}=${it.value.getAndSet(0)}" }
        if (recompositions.isNotEmpty()) {
            Log.d(TAG, "recompositions: $recompositions")
        }
    }
}

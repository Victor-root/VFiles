/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.zhanghai.android.files.test.idleMainLooperUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class StatefulLoaderTest {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        coroutineScope.cancel()
    }

    @Test
    fun loadsInTheBackground() {
        var loadingThread: Thread? = null
        val loader = StatefulLoader(coroutineScope) {
            loadingThread = Thread.currentThread()
            "a"
        }
        assertEquals(Loading<String>(null), loader.value)
        loader.load()
        idleMainLooperUntil { loader.value is Success }
        assertEquals(Success("a"), loader.value)
        assertEquals(loader.value, loader.flow.value)
        assertNotSame(Looper.getMainLooper().thread, loadingThread)
    }

    @Test
    fun keepsThePreviousValueWhileLoadingAgain() {
        var result = "a"
        var blockingLatch = CountDownLatch(0)
        val loader = StatefulLoader(coroutineScope) {
            blockingLatch.await(5, TimeUnit.SECONDS)
            result
        }
        loader.load()
        idleMainLooperUntil { loader.value is Success }
        result = "b"
        blockingLatch = CountDownLatch(1)
        loader.load()
        assertEquals(Loading("a"), loader.value)
        blockingLatch.countDown()
        idleMainLooperUntil { loader.value is Success }
        assertEquals(Success("b"), loader.value)
    }

    @Test
    fun keepsThePreviousValueOnFailure() {
        var isFailing = false
        val loader = StatefulLoader(coroutineScope) {
            if (isFailing) {
                throw IOException()
            }
            "a"
        }
        loader.load()
        idleMainLooperUntil { loader.value is Success }
        isFailing = true
        loader.load()
        idleMainLooperUntil { loader.value is Failure }
        // The exception may be a copy, with the stack trace of the coroutine added.
        val failure = loader.value as Failure
        assertEquals("a", failure.value)
        assertTrue(failure.throwable is IOException)
    }

    @Test
    fun interruptsAndDropsTheLoadingCancelledByLoadingAgain() {
        val isFirstLoad = AtomicBoolean(true)
        val firstLoadStarted = CountDownLatch(1)
        val firstLoadInterrupted = CountDownLatch(1)
        val loader = StatefulLoader(coroutineScope) {
            if (isFirstLoad.getAndSet(false)) {
                firstLoadStarted.countDown()
                try {
                    Thread.sleep(5_000)
                } catch (e: InterruptedException) {
                    firstLoadInterrupted.countDown()
                }
                "first"
            } else {
                "second"
            }
        }
        loader.load()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(firstLoadStarted.await(5, TimeUnit.SECONDS))
        loader.load()
        assertTrue(firstLoadInterrupted.await(5, TimeUnit.SECONDS))
        idleMainLooperUntil { loader.value is Success }
        assertEquals(Success("second"), loader.value)
        // The first loading has returned by now, and its result must not replace the second one.
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Success("second"), loader.value)
    }

    @Test
    fun setValueCancelsLoading() {
        val latch = CountDownLatch(1)
        val loader = StatefulLoader(coroutineScope) {
            latch.await(5, TimeUnit.SECONDS)
            "loaded"
        }
        loader.load()
        loader.setValue("set")
        latch.countDown()
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Success("set"), loader.value)
    }
}

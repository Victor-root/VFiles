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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CollectWhileSubscribedTest {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        coroutineScope.cancel()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun defersValuesWhileUnsubscribed() {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val subscriptionCount = MutableStateFlow(1)
        var actionCount = 0
        coroutineScope.launch {
            changes.collectWhileSubscribed(subscriptionCount) { ++actionCount }
        }
        idle()
        changes.tryEmit(Unit)
        idle()
        assertEquals(1, actionCount)
        subscriptionCount.value = 0
        idle()
        changes.tryEmit(Unit)
        idle()
        changes.tryEmit(Unit)
        idle()
        assertEquals(1, actionCount)
        // The values while unsubscribed result in only one action, once subscribed again.
        subscriptionCount.value = 2
        idle()
        assertEquals(2, actionCount)
        subscriptionCount.value = 0
        idle()
        subscriptionCount.value = 1
        idle()
        assertEquals(2, actionCount)
        changes.tryEmit(Unit)
        idle()
        assertEquals(3, actionCount)
    }

    @Test
    fun keepsWaitingAfterTheFlowCompletes() {
        // Like a path that can't be observed, whose loader must stay usable.
        val job = coroutineScope.launch {
            emptyFlow<Unit>().collectWhileSubscribed(MutableStateFlow(1)) {}
        }
        idle()
        assertTrue(job.isActive)
    }
}

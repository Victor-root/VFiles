/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DerivedStateFlowTest {
    @Test
    fun computesAgainWhenASourceChanges() {
        val first = MutableStateFlow(1)
        val second = MutableStateFlow(10)
        val sum = derivedStateFlow(first, second) { first.value + second.value }
        assertEquals(11, sum.value)
        first.value = 2
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(12, sum.value)
        second.value = 20
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(22, sum.value)
    }
}

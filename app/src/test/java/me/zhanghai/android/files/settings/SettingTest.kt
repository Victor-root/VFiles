/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import me.zhanghai.android.files.test.AppProviderRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingTest {
    @get:Rule
    val appProviderRule = AppProviderRule()

    @Test
    fun valueAndFlowFollowPutValue() {
        val setting = Settings.FILE_LIST_SHOW_HIDDEN_FILES
        val values = mutableListOf<Boolean>()
        setting.putValue(false)
        values += setting.flow.value
        setting.putValue(true)
        // Read right away, like the code changing a setting and then reading it.
        assertEquals(true, setting.value)
        values += setting.flow.value
        setting.putValue(false)
        values += setting.value
        assertEquals(listOf(false, true, false), values)
    }
}

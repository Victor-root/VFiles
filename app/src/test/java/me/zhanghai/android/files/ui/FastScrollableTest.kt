/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FastScrollableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setGrid(columnCount: Int): LazyGridState {
        val state = LazyGridState()
        composeTestRule.setContent {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier.size(300.dp, 500.dp),
                state = state
            ) {
                items((1..99).toList()) { BasicText("$it", Modifier.height(50.dp)) }
            }
        }
        return state
    }

    private fun LazyGridState.scroll(pixels: Float) {
        composeTestRule.runOnIdle { runBlocking { scrollBy(pixels) } }
    }

    private fun assertScrollFractionGrowsWithinARow(columnCount: Int) {
        val state = setGrid(columnCount)
        val scrollable = state.asFastScrollable()
        var fraction = composeTestRule.runOnIdle { scrollable.scrollFraction }
        assertEquals(0f, fraction)
        repeat(20) {
            state.scroll(7f)
            val newFraction = composeTestRule.runOnIdle { scrollable.scrollFraction }
            assertTrue(newFraction > fraction)
            fraction = newFraction
        }
        state.scroll(1_000_000f)
        assertEquals(1f, composeTestRule.runOnIdle { scrollable.scrollFraction })
    }

    @Test
    fun scrollFractionGrowsWithinARowInList() {
        assertScrollFractionGrowsWithinARow(1)
    }

    @Test
    fun scrollFractionGrowsWithinARowInGrid() {
        assertScrollFractionGrowsWithinARow(3)
    }

    @Test
    fun scrollsToFraction() {
        val state = setGrid(3)
        val scrollable = state.asFastScrollable()
        composeTestRule.runOnIdle { runBlocking { scrollable.scrollToFraction(0.5f) } }
        assertEquals(0.5f, composeTestRule.runOnIdle { scrollable.scrollFraction }, 0.01f)
    }
}

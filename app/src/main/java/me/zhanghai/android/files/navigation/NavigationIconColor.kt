/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import androidx.compose.ui.graphics.Color

/** A color for the icon of a drawer item, with a lighter variant for dark mode. */
enum class NavigationIconColor(private val light: Color, private val dark: Color) {
    GREY(Color(0xFF607D8B), Color(0xFF90A4AE)),
    BLUE(Color(0xFF1E88E5), Color(0xFF64B5F6)),
    ORANGE(Color(0xFFFB8C00), Color(0xFFFFB74D)),
    GREEN(Color(0xFF43A047), Color(0xFF81C784)),
    PURPLE(Color(0xFF8E24AA), Color(0xFFCE93D8)),
    TEAL(Color(0xFF00897B), Color(0xFF4DB6AC)),
    PINK(Color(0xFFD81B60), Color(0xFFF48FB1));

    fun getColor(isDark: Boolean): Color = if (isDark) dark else light
}

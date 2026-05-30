/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.colorpicker

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.GridView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.MaterialPreferenceDialogFragmentCompat
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.withTheme

class ColorPreferenceDialogFragment : MaterialPreferenceDialogFragmentCompat() {
    override val preference: BaseColorPreference
        get() = super.preference as BaseColorPreference

    private lateinit var colors: IntArray
    // The fixed colors shown in the grid; this excludes the dynamic color when present.
    private lateinit var gridColors: IntArray
    // Non-null when the first of [colors] is the wallpaper/dynamic color, shown apart from the grid.
    private var dynamicColor: Int? = null
    private var checkedColor = 0
    private var defaultColor = 0

    private lateinit var paletteGrid: GridView
    private lateinit var dynamicColorSwatch: ColorSwatchView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val preference = preference
            colors = preference.entryValues
            dynamicColor = preference.leadingDynamicColor
            checkedColor = preference.value
            defaultColor = preference.defaultValue
        } else {
            val state = savedInstanceState.getState<State>()
            colors = state.colors
            dynamicColor = state.dynamicColor
            checkedColor = state.checkedColor
            defaultColor = state.defaultColor
        }
        gridColors = if (dynamicColor != null && colors.isNotEmpty()) {
            colors.copyOfRange(1, colors.size)
        } else {
            colors
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(
            State(colors, dynamicColor, currentCheckedColor() ?: checkedColor, defaultColor)
        )
    }

    override fun onCreateDialogView(context: Context): View? =
        super.onCreateDialogView(context.withTheme(theme))

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        paletteGrid = ViewCompat.requireViewById(view, R.id.palette)
        paletteGrid.adapter = ColorPaletteAdapter(gridColors)
        paletteGrid.setOnItemClickListener { _, _, _, _ ->
            // The single-choice grid checks the tapped swatch itself; we just clear the dynamic one.
            if (dynamicColor != null) {
                dynamicColorSwatch.isChecked = false
            }
        }

        val dynamicColor = dynamicColor
        if (dynamicColor != null) {
            ViewCompat.requireViewById<View>(view, R.id.dynamicColorSection).isVisible = true
            ViewCompat.requireViewById<View>(view, R.id.dynamicColorDivider).isVisible = true
            ViewCompat.requireViewById<View>(view, R.id.presetColorsLabel).isVisible = true
            dynamicColorSwatch = ViewCompat.requireViewById(view, R.id.dynamicColorSwatch)
            dynamicColorSwatch.setColor(dynamicColor)
            dynamicColorSwatch.setOnClickListener {
                clearGridChoice()
                dynamicColorSwatch.isChecked = true
            }
        }

        if (dynamicColor != null && checkedColor == dynamicColor) {
            dynamicColorSwatch.isChecked = true
        } else {
            val checkedPosition = gridColors.indexOf(checkedColor)
            if (checkedPosition != -1) {
                paletteGrid.setItemChecked(checkedPosition, true)
            }
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        if (defaultColor in colors) {
            builder.setNeutralButton(R.string.default_, null)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as AlertDialog).apply {
            if (defaultColor in colors) {
                // Override the listener here so that we won't close the dialog.
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        selectColor(defaultColor)
                    }
                }
            }
        }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) {
            return
        }
        val checkedColor = currentCheckedColor() ?: return
        preference.value = checkedColor
    }

    private fun selectColor(@ColorInt color: Int) {
        if (dynamicColor != null && color == dynamicColor) {
            clearGridChoice()
            dynamicColorSwatch.isChecked = true
            return
        }
        if (dynamicColor != null) {
            dynamicColorSwatch.isChecked = false
        }
        val checkedPosition = gridColors.indexOf(color)
        if (checkedPosition != -1) {
            paletteGrid.setItemChecked(checkedPosition, true)
        }
    }

    private fun clearGridChoice() {
        val checkedPosition = paletteGrid.checkedItemPosition
        if (checkedPosition != AdapterView.INVALID_POSITION) {
            paletteGrid.setItemChecked(checkedPosition, false)
        }
    }

    // The currently selected color, or null when nothing is checked (so callers can decide whether
    // to fall back to the stored value or skip committing).
    private fun currentCheckedColor(): Int? {
        val dynamicColor = dynamicColor
        if (dynamicColor != null && ::dynamicColorSwatch.isInitialized
            && dynamicColorSwatch.isChecked) {
            return dynamicColor
        }
        if (::paletteGrid.isInitialized) {
            val checkedPosition = paletteGrid.checkedItemPosition
            if (checkedPosition != AdapterView.INVALID_POSITION) {
                return gridColors[checkedPosition]
            }
        }
        return null
    }

    @Parcelize
    private class State(
        val colors: IntArray,
        val dynamicColor: Int?,
        val checkedColor: Int,
        val defaultColor: Int
    ) : ParcelableState
}

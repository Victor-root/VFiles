/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import com.takisoft.preferencex.PreferenceFragmentCompat
import me.zhanghai.android.files.colorpicker.BaseColorPreference
import me.zhanghai.android.files.colorpicker.ColorPreferenceDialogFragment
import me.zhanghai.android.files.compat.getColorCompat

class ThemeColorPreference : BaseColorPreference {
    // The order colors are shown in the picker: the "dynamic" (wallpaper) color first, then the
    // fixed colors. The dynamic color only makes sense on Android 12+, so it is omitted below that.
    // This display order is intentionally decoupled from ThemeColor's ordinals, which is what gets
    // persisted, so reordering the picker never changes a saved value.
    private val entryThemeColors: List<ThemeColor> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(ThemeColor.DYNAMIC)
        }
        addAll(ThemeColor.entries.filter { it != ThemeColor.DYNAMIC })
    }

    private lateinit var _stringValue: String
    var stringValue: String
        get() = _stringValue
        set(value) {
            _stringValue = value
            persistString(value)
            notifyChanged()
        }

    // We can't use lateinit for Int.
    private var initialValue: Int? = null
    override var value: Int
        // Deliberately only bind for the initial value, because we are going to restart the
        // activity upon change and we want to let the activity animation have the correct visual
        // appearance.
        @ColorInt
        get() {
            var initialValue = initialValue
            if (initialValue == null) {
                initialValue = stringValue.toThemeColor().displayColor()
                this.initialValue = initialValue
            }
            return initialValue
        }
        set(value) {
            val index = entryValues.indexOf(value)
            val themeColor = if (index != -1) entryThemeColors[index] else ThemeColor.entries[0]
            stringValue = themeColor.ordinal.toString()
        }

    private lateinit var defaultStringValue: String
    override val defaultValue: Int
        @ColorInt
        get() = defaultStringValue.toThemeColor().displayColor()

    override var entryValues: IntArray
        private set

    // On Android 12+ the first entry is ThemeColor.DYNAMIC, shown apart in the picker.
    @get:ColorInt
    override val leadingDynamicColor: Int?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ThemeColor.DYNAMIC.displayColor()
        } else {
            null
        }

    private fun String.toThemeColor(): ThemeColor =
        ThemeColor.entries.getOrElse(toInt()) { ThemeColor.entries[0] }

    // The swatch color shown for a theme color. The "wallpaper" (dynamic) entry reflects the real
    // wallpaper color instead of the system accent, so it stays correct on OEM skins whose accent
    // does not follow the wallpaper; it falls back to the resource (system accent) when the
    // wallpaper color can't be read.
    @ColorInt
    private fun ThemeColor.displayColor(): Int =
        if (this == ThemeColor.DYNAMIC) {
            context.wallpaperAccentColor() ?: context.getColorCompat(resourceId)
        } else {
            context.getColorCompat(resourceId)
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        entryValues = entryThemeColors.map { it.displayColor() }.toIntArray()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? =
        a.getString(index).also { defaultStringValue = it!! }

    override fun onSetInitialValue(defaultValue: Any?) {
        stringValue = getPersistedString(defaultValue as String?)
    }

    companion object {
        init {
            PreferenceFragmentCompat.registerPreferenceFragment(
                ThemeColorPreference::class.java, ColorPreferenceDialogFragment::class.java
            )
        }
    }
}

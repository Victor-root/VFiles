/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.annotation.AnyRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.defaultSharedPreferences
import me.zhanghai.android.files.compat.PreferenceManagerCompat

/**
 * A value of the shared preferences, kept up to date in [flow], including when it's changed from
 * elsewhere.
 */
abstract class Setting<T>(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @param:AnyRes private val defaultValueRes: Int
) : OnSharedPreferenceChangeListener {
    private val sharedPreferences = getSharedPreferences(nameSuffix)
    private val key = getKey(keyRes, keySuffix)
    private var defaultValue: T? = null

    private lateinit var _flow: MutableStateFlow<T>
    val flow: StateFlow<T>
        get() = _flow

    val value: T
        get() = _flow.value

    constructor(@StringRes keyRes: Int, @AnyRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    protected fun init() {
        defaultValue = getDefaultValue(defaultValueRes)
        loadValue()
        // Only a weak reference is stored so we don't need to worry about unregistering.
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun getSharedPreferences(nameSuffix: String?): SharedPreferences =
        if (nameSuffix == null) {
            defaultSharedPreferences
        } else {
            val name = "${PreferenceManagerCompat.getDefaultSharedPreferencesName(application)
            }_$nameSuffix"
            val mode = PreferenceManagerCompat.defaultSharedPreferencesMode
            application.getSharedPreferences(name, mode)
        }

    private fun getKey(@StringRes keyRes: Int, keySuffix: String?): String {
        val key = application.getString(keyRes)
        return if (keySuffix != null) "${key}_$keySuffix" else key
    }

    protected abstract fun getDefaultValue(@AnyRes defaultValueRes: Int): T

    private fun loadValue() {
        @Suppress("UNCHECKED_CAST")
        val value = getValue(sharedPreferences, key, defaultValue as T)
        if (this::_flow.isInitialized) {
            _flow.value = value
        } else {
            _flow = MutableStateFlow(value)
        }
    }

    protected abstract fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: T
    ): T

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == this.key) {
            loadValue()
        }
    }

    fun putValue(value: T) {
        putValue(sharedPreferences, key, value)
    }

    protected abstract fun putValue(sharedPreferences: SharedPreferences, key: String, value: T)
}

/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.SharedPreferences
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.AnyRes
import androidx.annotation.ArrayRes
import androidx.annotation.BoolRes
import androidx.annotation.DimenRes
import androidx.annotation.IntegerRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import me.zhanghai.android.files.app.PARCEL_VAL_LIST
import me.zhanghai.android.files.app.PARCEL_VAL_PARCELABLE
import me.zhanghai.android.files.app.appClassLoader
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.Base64
import me.zhanghai.android.files.util.asBase64
import me.zhanghai.android.files.util.getBoolean
import me.zhanghai.android.files.util.getFloat
import me.zhanghai.android.files.util.getInteger
import me.zhanghai.android.files.util.getStringArray
import me.zhanghai.android.files.util.toBase64
import me.zhanghai.android.files.util.toByteArray
import me.zhanghai.android.files.util.use
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StringSetting(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @StringRes defaultValueRes: Int
) : Setting<String>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    constructor(@StringRes keyRes: Int, @StringRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    init {
        init()
    }

    override fun getDefaultValue(@StringRes defaultValueRes: Int): String =
        application.getString(defaultValueRes)

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: String
    ): String = sharedPreferences.getString(key, defaultValue)!!

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: String) {
        sharedPreferences.edit { putString(key, value) }
    }
}

class StringSetSetting(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @ArrayRes defaultValueRes: Int
) : Setting<Set<String>>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    constructor(@StringRes keyRes: Int, @ArrayRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    init {
        init()
    }

    override fun getDefaultValue(@StringRes defaultValueRes: Int): Set<String> =
        application.getStringArray(defaultValueRes).toSet()

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: Set<String>
    ): Set<String> = sharedPreferences.getStringSet(key, defaultValue)!!

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: Set<String>) {
        sharedPreferences.edit { putStringSet(key, value) }
    }
}

class IntegerSetting(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @IntegerRes defaultValueRes: Int
) : Setting<Int>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    constructor(@StringRes keyRes: Int, @IntegerRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    init {
        init()
    }

    override fun getDefaultValue(@IntegerRes defaultValueRes: Int): Int =
        application.getInteger(defaultValueRes)

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: Int
    ): Int = sharedPreferences.getInt(key, defaultValue)

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: Int) {
        sharedPreferences.edit { putInt(key, value) }
    }
}

class FloatSetting(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @DimenRes defaultValueRes: Int
) : Setting<Float>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    constructor(@StringRes keyRes: Int, @DimenRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    init {
        init()
    }

    override fun getDefaultValue(@DimenRes defaultValueRes: Int): Float =
        application.getFloat(defaultValueRes)

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: Float
    ): Float = sharedPreferences.getFloat(key, defaultValue)

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: Float) {
        sharedPreferences.edit { putFloat(key, value) }
    }
}

class BooleanSetting(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @BoolRes defaultValueRes: Int
) : Setting<Boolean>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    constructor(@StringRes keyRes: Int, @BoolRes defaultValueRes: Int) : this(
        null, keyRes, null, defaultValueRes
    )

    init {
        init()
    }

    override fun getDefaultValue(@BoolRes defaultValueRes: Int): Boolean =
        application.getBoolean(defaultValueRes)

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: Boolean
    ): Boolean = sharedPreferences.getBoolean(key, defaultValue)

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: Boolean) {
        sharedPreferences.edit { putBoolean(key, value) }
    }
}

// Use string resource for default value so that we can support ListPreference.
// TODO: kotlinc: Type argument is not within its bounds: should be subtype of 'Enum<E>'
//  https://youtrack.jetbrains.com/issue/KT-60985
//class EnumSetting<E : Enum<E>?>(
class EnumSetting<E : Enum<*>?>(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    @StringRes defaultValueRes: Int,
    enumClass: Class<E>
) : Setting<E>(nameSuffix, keyRes, keySuffix, defaultValueRes) {
    private val enumValues = enumClass.enumConstants!!

    constructor(
        @StringRes keyRes: Int,
        @StringRes defaultValueRes: Int,
        enumClass: Class<E>
    ) : this(null, keyRes, null, defaultValueRes, enumClass)

    init {
        init()
    }

    override fun getDefaultValue(@StringRes defaultValueRes: Int): E =
        if (defaultValueRes != ResourcesCompat.ID_NULL) {
            enumValues[application.getString(defaultValueRes).toInt()]
        } else {
            @Suppress("UNCHECKED_CAST")
            null as E
        }

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: E
    ): E {
        val valueOrdinal = sharedPreferences.getString(key, null)?.toInt() ?: return defaultValue
        return if (valueOrdinal in enumValues.indices) enumValues[valueOrdinal] else defaultValue
    }

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: E) {
        sharedPreferences.edit { putString(key, value?.ordinal?.toString()) }
    }
}

class ParcelValueSetting<T>(
    nameSuffix: String?,
    @StringRes keyRes: Int,
    keySuffix: String?,
    private val defaultValue: T
) : Setting<T>(nameSuffix, keyRes, keySuffix, 0) {
    constructor(@StringRes keyRes: Int, defaultValue: T) : this(null, keyRes, null, defaultValue)

    init {
        init()
    }

    override fun getDefaultValue(@AnyRes defaultValueRes: Int): T = defaultValue

    override fun getValue(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: T
    ): T =
        try {
            @Suppress("UNCHECKED_CAST")
            sharedPreferences.getString(key, null)?.asBase64()?.toParcelValue() as T?
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: defaultValue

    override fun putValue(sharedPreferences: SharedPreferences, key: String, value: T) {
        sharedPreferences.edit { putString(key, value?.toParcelBase64()?.value) }
    }
}

internal fun Base64.toParcelValue(): Any? {
    val bytes = toByteArray()
    return Parcel.obtain().use { parcel ->
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            isParcelValueBeforeTiramisu(bytes)) {
            parcel.readParcelValueBeforeTiramisu()
        } else {
            parcel.readValue(appClassLoader)
        }
    }
}

/**
 * Android 13 added the length of the value after its type, so what was stored before updating to
 * it can't be read with [Parcel.readValue] anymore.
 */
internal fun isParcelValueBeforeTiramisu(bytes: ByteArray): Boolean {
    if (bytes.size < 2 * Int.SIZE_BYTES) {
        return false
    }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val valueType = buffer.getInt()
    if (valueType != PARCEL_VAL_PARCELABLE && valueType != PARCEL_VAL_LIST) {
        return false
    }
    val isEmptyList = valueType == PARCEL_VAL_LIST && bytes.size == 2 * Int.SIZE_BYTES
    return isEmptyList || buffer.getInt() != buffer.remaining()
}

private fun Parcel.readParcelValueBeforeTiramisu(): Any? =
    when (val valueType = readInt()) {
        PARCEL_VAL_PARCELABLE -> readParcelable<Parcelable>(appClassLoader)
        PARCEL_VAL_LIST -> List(readInt()) { readParcelValueBeforeTiramisu() }
        else -> throw IllegalArgumentException("Unexpected value type $valueType")
    }

internal fun Any?.toParcelBase64(): Base64 {
    val bytes = Parcel.obtain().use { parcel ->
        parcel.writeValue(this)
        parcel.marshall()
    }
    return bytes.toBase64()
}

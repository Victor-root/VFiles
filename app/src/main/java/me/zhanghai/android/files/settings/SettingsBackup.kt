/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.app.LocaleManager
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.core.app.LocaleManagerCompat
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.PARCEL_VAL_LIST
import me.zhanghai.android.files.app.PARCEL_VAL_PARCELABLE
import me.zhanghai.android.files.app.appClassLoader
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.asBase64
import me.zhanghai.android.files.util.toBase64
import me.zhanghai.android.files.util.toByteArray
import me.zhanghai.android.files.util.use
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class SettingsBackupException(@param:StringRes val messageRes: Int) : IOException()

class InvalidSettingsBackupException :
    SettingsBackupException(R.string.settings_backup_restore_error_file)

class WrongSettingsBackupPasswordException :
    SettingsBackupException(R.string.settings_backup_restore_error_password)

class NewerSettingsBackupException :
    SettingsBackupException(R.string.settings_backup_restore_error_version)

/**
 * All the shared preferences in a file encrypted with a password, because they include the
 * passwords of the servers.
 */
object SettingsBackup {
    private val MAGIC = "VFILESBK".toByteArray()
    private const val FORMAT_VERSION = 1
    private const val MAX_FILE_SIZE = 16 * 1024 * 1024

    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_ITERATIONS = 600_000
    private const val MAX_KEY_ITERATIONS = 10_000_000
    private const val KEY_SIZE_BITS = 256
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_SIZE_BITS = 128
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12

    private const val JSON_VERSION_CODE = "versionCode"
    private const val JSON_LOCALES = "locales"
    private const val JSON_PREFERENCES = "preferences"
    private const val JSON_TYPE = "type"
    private const val JSON_VALUE = "value"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "stringSet"
    private const val TYPE_PARCELABLE = "parcelable"
    private const val TYPE_PARCELABLE_LIST = "parcelableList"

    private const val NAME_DEFAULT = "default"

    private val sharedPreferences: Map<String, SharedPreferences>
        get() = mapOf(
            NAME_DEFAULT to getSettingSharedPreferences(null),
            PathSettings.NAME_SUFFIX to getSettingSharedPreferences(PathSettings.NAME_SUFFIX)
        )

    private var locales: String
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LocaleManagerCompat.getApplicationLocales(application).toLanguageTags()
            } else {
                Settings.LOCALES.value
            }
        set(value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(value)
            }
        }

    @Throws(IOException::class)
    fun write(outputStream: OutputStream, password: String) {
        val json = JSONObject()
            .put(JSON_VERSION_CODE, BuildConfig.VERSION_CODE)
            .put(JSON_LOCALES, locales)
            .put(
                JSON_PREFERENCES,
                JSONObject().apply {
                    sharedPreferences.forEach { (name, preferences) ->
                        put(name, preferences.all.toJson())
                    }
                }
            )
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val header = createHeader(KEY_ITERATIONS, salt, iv)
        val encryptedJson = try {
            createCipher(Cipher.ENCRYPT_MODE, password, KEY_ITERATIONS, salt, iv, header)
                .doFinal(json.toString().toByteArray())
        } catch (e: GeneralSecurityException) {
            throw IOException(e)
        }
        outputStream.write(header)
        outputStream.write(encryptedJson)
    }

    /** Nothing is changed unless the whole backup is valid. The app has to restart afterwards. */
    @Throws(IOException::class)
    fun restore(inputStream: InputStream, password: String) {
        val json = decrypt(inputStream.readAllBytesUpTo(MAX_FILE_SIZE), password)
        val editors = try {
            if (json.getInt(JSON_VERSION_CODE) > BuildConfig.VERSION_CODE) {
                throw NewerSettingsBackupException()
            }
            val jsonPreferences = json.getJSONObject(JSON_PREFERENCES)
            sharedPreferences.map { (name, preferences) ->
                preferences.edit().clear().apply {
                    putJson(jsonPreferences.getJSONObject(name))
                }
            }
        } catch (e: JSONException) {
            throw InvalidSettingsBackupException().apply { initCause(e) }
        } catch (e: RuntimeException) {
            // Decoding the parcelables of a damaged backup.
            throw InvalidSettingsBackupException().apply { initCause(e) }
        }
        val locales = json.optString(JSON_LOCALES)
        // Applied by ourselves before Android 13.
        editors.first().putString(application.getString(R.string.pref_key_locales), locales)
        if (!editors.all { it.commit() }) {
            throw IOException("SharedPreferences.Editor.commit() failed")
        }
        this.locales = locales
    }

    private fun Map<String, *>.toJson(): JSONObject =
        JSONObject().also { json ->
            forEach { (key, value) ->
                val (type, jsonValue) = when (value) {
                    is Boolean -> TYPE_BOOLEAN to value
                    is Float -> TYPE_FLOAT to value.toDouble()
                    is Int -> TYPE_INT to value
                    is Long -> TYPE_LONG to value
                    is String -> value.toPortableParcelValue() ?: (TYPE_STRING to value)
                    is Set<*> -> TYPE_STRING_SET to JSONArray(value)
                    else -> return@forEach
                }
                json.put(key, JSONObject().put(JSON_TYPE, type).put(JSON_VALUE, jsonValue))
            }
        }

    /**
     * The format of [android.os.Parcel.writeValue], which the settings are stored with, depends on
     * the Android version, unlike the one of [Parcelable]s themselves.
     */
    private fun String.toPortableParcelValue(): Pair<String, Any>? {
        val value = try {
            val base64 = asBase64()
            val bytes = base64.toByteArray()
            if (bytes.size < Int.SIZE_BYTES) {
                return null
            }
            val valueType = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt()
            if (valueType != PARCEL_VAL_PARCELABLE && valueType != PARCEL_VAL_LIST) {
                return null
            }
            base64.toParcelValue()
        } catch (e: Exception) {
            return null
        }
        return when {
            value is Parcelable -> TYPE_PARCELABLE to value.toPortableBase64()
            value is List<*> && value.all { it is Parcelable } ->
                TYPE_PARCELABLE_LIST to
                    JSONArray(value.map { (it as Parcelable).toPortableBase64() })
            else -> null
        }
    }

    private fun Parcelable.toPortableBase64(): String {
        val bytes = Parcel.obtain().use { parcel ->
            parcel.writeParcelable(this, 0)
            parcel.marshall()
        }
        return bytes.toBase64().value
    }

    private fun String.fromPortableBase64(): Parcelable {
        val bytes = asBase64().toByteArray()
        return Parcel.obtain().use { parcel ->
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            parcel.readParcelable(appClassLoader)
        } ?: throw JSONException("Null parcelable")
    }

    @Throws(JSONException::class)
    private fun SharedPreferences.Editor.putJson(json: JSONObject) {
        for (key in json.keys()) {
            val entry = json.getJSONObject(key)
            when (val type = entry.getString(JSON_TYPE)) {
                TYPE_BOOLEAN -> putBoolean(key, entry.getBoolean(JSON_VALUE))
                TYPE_FLOAT -> putFloat(key, entry.getDouble(JSON_VALUE).toFloat())
                TYPE_INT -> putInt(key, entry.getInt(JSON_VALUE))
                TYPE_LONG -> putLong(key, entry.getLong(JSON_VALUE))
                TYPE_STRING -> putString(key, entry.getString(JSON_VALUE))
                TYPE_STRING_SET -> {
                    val array = entry.getJSONArray(JSON_VALUE)
                    putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                }
                TYPE_PARCELABLE -> putString(
                    key, entry.getString(JSON_VALUE).fromPortableBase64().toParcelBase64().value
                )
                TYPE_PARCELABLE_LIST -> {
                    val array = entry.getJSONArray(JSON_VALUE)
                    val value = (0 until array.length()).map {
                        array.getString(it).fromPortableBase64()
                    }
                    putString(key, value.toParcelBase64().value)
                }
                else -> throw JSONException("Unknown type $type")
            }
        }
    }

    private fun createHeader(keyIterations: Int, salt: ByteArray, iv: ByteArray): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).apply {
                write(MAGIC)
                writeByte(FORMAT_VERSION)
                writeInt(keyIterations)
                write(salt)
                write(iv)
            }
            bytes.toByteArray()
        }

    @Throws(IOException::class)
    private fun decrypt(bytes: ByteArray, password: String): JSONObject {
        val salt = ByteArray(SALT_SIZE)
        val iv = ByteArray(IV_SIZE)
        val keyIterations = try {
            DataInputStream(bytes.inputStream()).run {
                val magic = ByteArray(MAGIC.size).also { readFully(it) }
                if (!magic.contentEquals(MAGIC) || readByte().toInt() != FORMAT_VERSION) {
                    throw InvalidSettingsBackupException()
                }
                readInt().also {
                    readFully(salt)
                    readFully(iv)
                }
            }
        } catch (e: EOFException) {
            throw InvalidSettingsBackupException().apply { initCause(e) }
        }
        if (keyIterations !in 1..MAX_KEY_ITERATIONS) {
            throw InvalidSettingsBackupException()
        }
        val header = createHeader(keyIterations, salt, iv)
        val decryptedBytes = try {
            createCipher(Cipher.DECRYPT_MODE, password, keyIterations, salt, iv, header)
                .doFinal(bytes, header.size, bytes.size - header.size)
        } catch (e: AEADBadTagException) {
            throw WrongSettingsBackupPasswordException().apply { initCause(e) }
        } catch (e: GeneralSecurityException) {
            throw InvalidSettingsBackupException().apply { initCause(e) }
        }
        return try {
            JSONObject(String(decryptedBytes))
        } catch (e: JSONException) {
            throw InvalidSettingsBackupException().apply { initCause(e) }
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun createCipher(
        mode: Int,
        password: String,
        keyIterations: Int,
        salt: ByteArray,
        iv: ByteArray,
        header: ByteArray
    ): Cipher {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, keyIterations, KEY_SIZE_BITS)
        val key = SecretKeyFactory.getInstance(KEY_ALGORITHM).generateSecret(keySpec).encoded
        return Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
            updateAAD(header)
        }
    }

    @Throws(IOException::class)
    private fun InputStream.readAllBytesUpTo(maxSize: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val readSize = read(buffer)
            if (readSize == -1) {
                return outputStream.toByteArray()
            }
            outputStream.write(buffer, 0, readSize)
            if (outputStream.size() > maxSize) {
                throw InvalidSettingsBackupException()
            }
        }
    }
}

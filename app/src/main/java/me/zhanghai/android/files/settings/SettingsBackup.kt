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
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.theme.custom.LauncherIcon
import me.zhanghai.android.files.util.asBase64
import me.zhanghai.android.files.util.readParcelable
import me.zhanghai.android.files.util.toBase64
import me.zhanghai.android.files.util.toByteArray
import me.zhanghai.android.files.util.use
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
 * All the shared preferences in a JSON file, which can be encrypted with a password as they include
 * the passwords of the servers.
 */
object SettingsBackup {
    private const val APP_NAME = "VFiles"
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

    private const val JSON_APP = "app"
    private const val JSON_FORMAT = "format"
    private const val JSON_ENCRYPTION = "encryption"
    private const val JSON_KEY_ITERATIONS = "keyIterations"
    private const val JSON_SALT = "salt"
    private const val JSON_IV = "iv"
    private const val JSON_DATA = "data"
    private const val JSON_INDENT_SPACES = 2
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

    /** Without a password, everything including the passwords of the servers can be read. */
    @Throws(IOException::class)
    fun write(outputStream: OutputStream, password: String?) {
        val content = createHeader()
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
        val json = if (password != null) content.encrypt(password) else content
        outputStream.write(json.toString(JSON_INDENT_SPACES).toByteArray())
    }

    @Throws(IOException::class)
    fun isEncrypted(inputStream: InputStream): Boolean =
        parseFile(inputStream).has(JSON_ENCRYPTION)

    /** Nothing is changed unless the whole backup is valid. The app has to restart afterwards. */
    @Throws(IOException::class)
    fun restore(inputStream: InputStream, password: String?) {
        val file = parseFile(inputStream)
        val json = if (file.has(JSON_ENCRYPTION)) file.decrypt(password) else file
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
        LauncherIcon.applySavedColor()
    }

    private fun createHeader(): JSONObject =
        JSONObject().put(JSON_APP, APP_NAME).put(JSON_FORMAT, FORMAT_VERSION)

    @Throws(IOException::class)
    private fun parseFile(inputStream: InputStream): JSONObject {
        val json = try {
            JSONObject(String(inputStream.readAllBytesUpTo(MAX_FILE_SIZE)))
        } catch (e: JSONException) {
            throw InvalidSettingsBackupException().apply { initCause(e) }
        }
        if (json.optString(JSON_APP) != APP_NAME) {
            throw InvalidSettingsBackupException()
        }
        if (json.optInt(JSON_FORMAT) > FORMAT_VERSION) {
            throw NewerSettingsBackupException()
        }
        return json
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
            parcel.readParcelable<Parcelable>()
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

    @Throws(IOException::class)
    private fun JSONObject.encrypt(password: String): JSONObject {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val data = try {
            createCipher(Cipher.ENCRYPT_MODE, password, KEY_ITERATIONS, salt, iv)
                .doFinal(toString().toByteArray())
        } catch (e: GeneralSecurityException) {
            throw IOException(e)
        }
        return createHeader()
            .put(
                JSON_ENCRYPTION,
                JSONObject()
                    .put(JSON_KEY_ITERATIONS, KEY_ITERATIONS)
                    .put(JSON_SALT, salt.toBase64().value)
                    .put(JSON_IV, iv.toBase64().value)
            )
            .put(JSON_DATA, data.toBase64().value)
    }

    @Throws(IOException::class)
    private fun JSONObject.decrypt(password: String?): JSONObject {
        if (password == null) {
            throw WrongSettingsBackupPasswordException()
        }
        return try {
            val encryption = getJSONObject(JSON_ENCRYPTION)
            val keyIterations = encryption.getInt(JSON_KEY_ITERATIONS)
            if (keyIterations !in 1..MAX_KEY_ITERATIONS) {
                throw InvalidSettingsBackupException()
            }
            val salt = encryption.getString(JSON_SALT).asBase64().toByteArray()
            val iv = encryption.getString(JSON_IV).asBase64().toByteArray()
            val data = getString(JSON_DATA).asBase64().toByteArray()
            JSONObject(
                String(
                    createCipher(Cipher.DECRYPT_MODE, password, keyIterations, salt, iv)
                        .doFinal(data)
                )
            )
        } catch (e: AEADBadTagException) {
            throw WrongSettingsBackupPasswordException().apply { initCause(e) }
        } catch (e: GeneralSecurityException) {
            throw InvalidSettingsBackupException().apply { initCause(e) }
        } catch (e: JSONException) {
            throw InvalidSettingsBackupException().apply { initCause(e) }
        } catch (e: IllegalArgumentException) {
            // Not Base64.
            throw InvalidSettingsBackupException().apply { initCause(e) }
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun createCipher(
        mode: Int,
        password: String,
        keyIterations: Int,
        salt: ByteArray,
        iv: ByteArray
    ): Cipher {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, keyIterations, KEY_SIZE_BITS)
        val key = SecretKeyFactory.getInstance(KEY_ALGORITHM).generateSecret(keySpec).encoded
        return Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
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

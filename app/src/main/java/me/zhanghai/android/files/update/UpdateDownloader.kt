/*
 * In-app self-update support. Fork addition.
 */

package me.zhanghai.android.files.update

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Streams a remote APK into the app's private cache. Runs synchronously — callers schedule it on a
 * background thread. The download lives under cacheDir/updates/, which is the path exposed by
 * res/xml/update_file_provider_paths.xml, so the system installer can read it through the dedicated
 * FileProvider without any external-storage permission.
 */
internal object UpdateDownloader {
    private const val TAG = "MaterialFiles-Updater"
    private const val UPDATES_DIR = "updates"
    private const val PART_SUFFIX = ".part"

    /** Returns the downloaded File on success, null on any failure. */
    fun downloadTo(context: Context, url: String, fileName: String): File? {
        val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        purgeOlder(dir, keep = fileName)

        val finalFile = File(dir, fileName)
        // Re-use a previous successful download for the same filename (e.g. the user dismissed the
        // install dialog last time) instead of pulling it down again.
        if (finalFile.exists() && finalFile.length() > 0) {
            return finalFile
        }

        val partFile = File(dir, fileName + PART_SUFFIX)
        partFile.delete()

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MaterialFiles-Android-Updater")
                connectTimeout = 15_000
                // No read timeout: large APKs over a slow link otherwise tear down mid-stream.
                readTimeout = 0
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "Download HTTP ${conn.responseCode} for $url")
                return null
            }
            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            // Atomic-ish rename so a half-written .part can never be mistaken for a complete APK.
            if (!partFile.renameTo(finalFile)) {
                partFile.delete()
                return null
            }
            finalFile
        } catch (e: Exception) {
            Log.w(TAG, "Download failed: ${e.message}")
            partFile.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Wipe everything under updates/ except [keep], so the cache never accumulates old APKs. */
    private fun purgeOlder(dir: File, keep: String) {
        dir.listFiles()?.forEach { file ->
            if (file.name != keep) {
                file.delete()
            }
        }
    }
}

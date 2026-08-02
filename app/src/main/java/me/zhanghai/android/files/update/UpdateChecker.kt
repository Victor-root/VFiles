/*
 * In-app self-update support. Fork addition.
 */

package me.zhanghai.android.files.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A newer release published on GitHub Releases.
 *
 * [versionName] is parsed out of the asset filename (e.g. "1.9.0" from "MaterialFiles-v1.9.0.apk").
 */
data class ReleaseInfo(
    val versionName: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

/**
 * Hits the GitHub Releases API and reports whether the latest release ships an APK newer than what
 * is currently installed. Synchronous on purpose: callers run it off the main thread.
 *
 * No external HTTP library: HttpURLConnection + org.json are part of the platform, so we don't drag
 * OkHttp/Retrofit in just to issue one request every twelve hours.
 */
internal object UpdateChecker {
    // Matches the APK names produced by the "MaterialFiles-v<version>.apk" output renaming in
    // build.gradle. The version sub-group is fed back verbatim for the comparison below.
    private val APK_NAME_REGEX = Regex("^MaterialFiles-v(.+)\\.apk$", RegexOption.IGNORE_CASE)

    /**
     * Fetches the latest release for `owner/repo`. Returns null when the request fails, the release
     * has no matching APK asset, or the published version is not strictly newer than
     * [currentVersion] (typically BuildConfig.VERSION_NAME).
     */
    fun checkLatest(repo: String, currentVersion: String): ReleaseInfo? {
        val json = fetchJson("https://api.github.com/repos/$repo/releases/latest") ?: return null

        // Pick the first APK asset, whatever it's named.
        val assets = json.optJSONArray("assets") ?: return null
        var assetName: String? = null
        var downloadUrl: String? = null
        var sizeBytes = -1L
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            if (!name.endsWith(".apk", ignoreCase = true)) {
                continue
            }
            val url = asset.optString("browser_download_url", "")
            if (url.isEmpty()) {
                continue
            }
            assetName = name
            downloadUrl = url
            sizeBytes = asset.optLong("size", -1L)
            break
        }
        val finalAssetName = assetName ?: return null
        val finalDownloadUrl = downloadUrl ?: return null

        // The published version comes from the release tag (e.g. "v1.9.0"); fall back to parsing it
        // out of the APK file name for releases that are tagged differently.
        val tagName = json.optString("tag_name", "")
        val rawVersion = tagName.ifEmpty {
            APK_NAME_REGEX.matchEntire(finalAssetName)?.groupValues?.getOrNull(1).orEmpty()
        }
        val version = normalise(rawVersion)
        if (version.isEmpty() || !isStrictlyNewer(version, currentVersion)) {
            return null
        }
        return ReleaseInfo(version, finalAssetName, finalDownloadUrl, sizeBytes)
    }

    private fun normalise(v: String) = v.trim().removePrefix("v").removePrefix("V")

    /**
     * Splits both versions on "." and "-", coerces each segment to an Int (non-numeric tokens are
     * treated as 0) and walks them left-to-right. Returns true iff [candidate] is strictly greater
     * than [installed], so "1.10.0" correctly beats "1.9.0" with no lexicographic pitfall.
     */
    internal fun isStrictlyNewer(candidate: String, installed: String): Boolean {
        val c = parseVersion(candidate)
        val i = parseVersion(installed)
        val n = maxOf(c.size, i.size)
        for (k in 0 until n) {
            val cp = c.getOrNull(k) ?: 0
            val ip = i.getOrNull(k) ?: 0
            if (cp != ip) {
                return cp > ip
            }
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> =
        normalise(v).split('.', '-').map { it.toIntOrNull() ?: 0 }

    private fun fetchJson(url: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // GitHub returns a richer JSON shape on this Accept type, and a descriptive
                // User-Agent is mandatory: the API refuses unidentified clients with HTTP 403.
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MaterialFiles-Android-Updater")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            if (conn.responseCode !in 200..299) {
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

/*
 * In-app self-update support. Fork addition.
 */

package me.zhanghai.android.files.update

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

/**
 * Silent, GitHub-Releases-backed self-updater.
 *
 *   1. [checkInBackground] hits the releases API on a daemon thread (throttled to once every 12h)
 *      and, when a newer "MaterialFiles-v<version>.apk" asset is published, records it in [Settings]
 *      so the navigation drawer can show an unobtrusive badge on "About".
 *
 *   2. The user opens About and taps the entry: [downloadAndInstall] streams the APK into the
 *      private cache and hands it to Android's system installer, which shows the standard "Update?"
 *      prompt. Nothing else is ever surfaced: no notifications, no dialogs of our own.
 *
 * Writing a [Settings] value updates its LiveData synchronously, so every write here is posted to
 * the main thread.
 */
object UpdateManager {
    private const val TAG = "MaterialFiles-Updater"
    private const val GITHUB_REPO = "Victor-root/VFiles"

    // Twelve hours: short enough that a freshly published release is noticed within the same day,
    // long enough to never approach GitHub's anonymous rate limit.
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

    private val checking = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Apps shipped through F-Droid get updates from F-Droid; cleanly step out of the way there.
    private val FDROID_INSTALLERS = setOf(
        "org.fdroid.fdroid",
        "org.fdroid.basic",
        "org.fdroid.fdroid.privileged"
    )

    /**
     * Throttled background check. Call from the main thread (app start, opening About). Reads the
     * throttle on the calling thread (which also forces [Settings] to initialise on the main
     * thread), then does only the network call off-thread.
     */
    fun checkInBackground() {
        if (isFdroidInstall()) {
            return
        }
        val now = System.currentTimeMillis()
        val lastCheck = Settings.UPDATE_LAST_CHECK_TIME.valueCompat
        if (lastCheck > 0L && now - lastCheck < CHECK_INTERVAL_MS) {
            return
        }
        if (!checking.compareAndSet(false, true)) {
            return
        }
        Thread({
            try {
                val release = UpdateChecker.checkLatest(GITHUB_REPO, BuildConfig.VERSION_NAME)
                mainHandler.post {
                    Settings.UPDATE_LAST_CHECK_TIME.putValue(now)
                    storeAvailableRelease(release)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Background update check failed", t)
            } finally {
                checking.set(false)
            }
        }, "MaterialFiles-UpdateCheck").apply { isDaemon = true }.start()
    }

    /**
     * Manual "check now" hook for the in-app About entry. Skips the throttle and reports the result
     * on the main thread via [onResult].
     */
    fun forceCheck(onResult: (ReleaseInfo?) -> Unit) {
        if (isFdroidInstall()) {
            onResult(null)
            return
        }
        if (!checking.compareAndSet(false, true)) {
            // An automatic check is mid-flight; let it finish.
            onResult(null)
            return
        }
        Thread({
            var result: ReleaseInfo? = null
            try {
                result = UpdateChecker.checkLatest(GITHUB_REPO, BuildConfig.VERSION_NAME)
            } catch (t: Throwable) {
                Log.w(TAG, "Manual update check failed", t)
            } finally {
                checking.set(false)
                val release = result
                val now = System.currentTimeMillis()
                mainHandler.post {
                    Settings.UPDATE_LAST_CHECK_TIME.putValue(now)
                    storeAvailableRelease(release)
                    onResult(release)
                }
            }
        }, "MaterialFiles-UpdateForceCheck").apply { isDaemon = true }.start()
    }

    /**
     * Silent background download followed by the system install prompt. [onResult] fires on the
     * main thread with `true` once the install intent was launched, `false` on any earlier failure.
     */
    fun downloadAndInstall(release: ReleaseInfo, onResult: (Boolean) -> Unit) {
        if (!downloading.compareAndSet(false, true)) {
            onResult(false)
            return
        }
        Thread({
            var ok = false
            try {
                val apk = UpdateDownloader.downloadTo(
                    application, release.downloadUrl, release.assetName
                )
                if (apk != null) {
                    ok = UpdateInstaller.install(application, apk)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Update download/install failed", t)
            } finally {
                downloading.set(false)
                val result = ok
                mainHandler.post { onResult(result) }
            }
        }, "MaterialFiles-UpdateDownload").apply { isDaemon = true }.start()
    }

    /**
     * The currently-known newer release, or null when we're already up to date. Pure read with no
     * side effects: safe to call from the main thread while building the navigation drawer.
     */
    fun getStoredRelease(): ReleaseInfo? {
        val version = Settings.UPDATE_AVAILABLE_VERSION.valueCompat
        val asset = Settings.UPDATE_AVAILABLE_ASSET_NAME.valueCompat
        val url = Settings.UPDATE_AVAILABLE_DOWNLOAD_URL.valueCompat
        if (version.isEmpty() || asset.isEmpty() || url.isEmpty()) {
            return null
        }
        // The stored release goes stale if the user updated by other means (built locally,
        // sideloaded); only surface it while it's strictly newer than what's installed.
        if (!UpdateChecker.isStrictlyNewer(version, BuildConfig.VERSION_NAME)) {
            return null
        }
        return ReleaseInfo(version, asset, url, -1L)
    }

    fun isUpdateAvailable(): Boolean = getStoredRelease() != null

    // Must run on the main thread. Guarded so an unchanged result doesn't needlessly re-emit the
    // setting (which would rebuild the navigation drawer).
    private fun storeAvailableRelease(release: ReleaseInfo?) {
        val version = release?.versionName ?: ""
        val asset = release?.assetName ?: ""
        val url = release?.downloadUrl ?: ""
        if (Settings.UPDATE_AVAILABLE_VERSION.valueCompat != version) {
            Settings.UPDATE_AVAILABLE_VERSION.putValue(version)
        }
        if (Settings.UPDATE_AVAILABLE_ASSET_NAME.valueCompat != asset) {
            Settings.UPDATE_AVAILABLE_ASSET_NAME.putValue(asset)
        }
        if (Settings.UPDATE_AVAILABLE_DOWNLOAD_URL.valueCompat != url) {
            Settings.UPDATE_AVAILABLE_DOWNLOAD_URL.putValue(url)
        }
    }

    private fun isFdroidInstall(): Boolean {
        val installer = installerPackage(application) ?: return false
        return installer in FDROID_INSTALLERS
    }

    private fun installerPackage(context: Context): String? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager
                    .getInstallSourceInfo(context.packageName)
                    .installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
}

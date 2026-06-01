/*
 * In-app self-update support. Fork addition.
 */

package me.zhanghai.android.files.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import me.zhanghai.android.files.R

/**
 * Hands a freshly-downloaded APK to Android's system installer. We never install silently — the
 * user only sees the standard "Update?" dialog, same as any sideloaded install.
 *
 * On Android 8+ the system handles the "Install unknown apps" toggle inline when ACTION_VIEW is
 * fired: if the special-access permission hasn't been granted yet, the toggle page is shown first
 * and the install dialog pops automatically once enabled. So we just fire the intent.
 */
internal object UpdateInstaller {
    /** Returns false if the intent can't be launched (no installer activity, FileProvider error). */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() == 0L) {
            return false
        }
        val authority = context.getString(R.string.update_provider_authority)
        val uri: Uri = try {
            FileProvider.getUriForFile(context, authority, apk)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

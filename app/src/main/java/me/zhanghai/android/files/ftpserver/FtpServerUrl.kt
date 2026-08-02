/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.RuntimeBroadcastReceiver
import me.zhanghai.android.files.util.getLocalAddress
import me.zhanghai.android.files.util.valueCompat
import java.net.InetAddress

object FtpServerUrl {
    fun getUrl(): String? {
        val localAddress = InetAddress::class.getLocalAddress() ?: return null
        val username = if (!Settings.FTP_SERVER_ANONYMOUS_LOGIN.valueCompat) {
            Settings.FTP_SERVER_USERNAME.valueCompat
        } else {
            null
        }
        val host = localAddress.hostAddress
        val port = Settings.FTP_SERVER_PORT.valueCompat
        return "ftp://${if (username != null) "$username@" else ""}$host:$port/"
    }

    // The modern replacement, ConnectivityManager.registerNetworkCallback(), is a different
    // registration/callback lifecycle entirely (not an IntentFilter/BroadcastReceiver), and this
    // still works: CONNECTIVITY_ACTION is only restricted for manifest-declared receivers, not one
    // registered at runtime like this. Not verifiable without a real device to trigger a network
    // change on.
    @Suppress("DEPRECATION")
    fun createChangeReceiver(context: Context, onChange: () -> Unit): RuntimeBroadcastReceiver =
        RuntimeBroadcastReceiver(
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onChange()
                }
            }, context
        )
}

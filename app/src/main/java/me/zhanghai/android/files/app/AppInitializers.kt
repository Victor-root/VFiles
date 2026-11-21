/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.webkit.WebView
import jcifs.context.SingletonContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.coil.initializeCoil
import me.zhanghai.android.files.filejob.fileJobNotificationTemplate
import me.zhanghai.android.files.ftpserver.ftpServerServiceNotificationTemplate
import me.zhanghai.android.files.hiddenapi.HiddenApi
import me.zhanghai.android.files.provider.FileSystemProviders
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.FtpServerAuthenticator
import me.zhanghai.android.files.storage.SftpServerAuthenticator
import me.zhanghai.android.files.storage.SmbServerAuthenticator
import me.zhanghai.android.files.storage.StorageVolumeList
import me.zhanghai.android.files.storage.WebDavServerAuthenticator
import java.util.Properties
import me.zhanghai.android.files.provider.ftp.client.Client as FtpClient
import me.zhanghai.android.files.provider.sftp.client.Client as SftpClient
import me.zhanghai.android.files.provider.smb.client.Client as SmbClient
import me.zhanghai.android.files.provider.webdav.client.Client as WebDavClient

val appInitializers = listOf(
    ::disableHiddenApiChecks,
    ::initializeWebViewDebugging,
    ::initializeCoil,
    ::initializeFileSystemProviders,
    ::upgradeApp,
    ::initializeStateObjects,
    ::createNotificationChannels
)

private fun disableHiddenApiChecks() {
    HiddenApi.disableHiddenApiChecks()
}

private fun initializeWebViewDebugging() {
    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}

/** The configuration of jCIFS, which scanning the LAN for SMB servers adjusts too. */
fun createJcifsProperties(): Properties =
    Properties().apply {
        setProperty("jcifs.netbios.cachePolicy", "0")
        setProperty("jcifs.smb.client.maxVersion", "SMB1")
    }

private fun initializeFileSystemProviders() {
    FileSystemProviders.install()
    FileSystemProviders.overflowWatchEvents = true
    // SingletonContext.init() calls NameServiceClientImpl.initCache() which connects to network.
    applicationScope.launch(Dispatchers.IO) { SingletonContext.init(createJcifsProperties()) }
    FtpClient.authenticator = FtpServerAuthenticator
    SftpClient.authenticator = SftpServerAuthenticator
    SmbClient.authenticator = SmbServerAuthenticator
    WebDavClient.authenticator = WebDavServerAuthenticator
}

private fun initializeStateObjects() {
    // Force initialization of the objects holding state so that it won't happen on a background
    // thread.
    StorageVolumeList.value
    Settings.FILE_LIST_DEFAULT_DIRECTORY.value
}

private fun createNotificationChannels() {
    notificationManager.createNotificationChannels(
        listOf(
            backgroundActivityStartNotificationTemplate.channelTemplate,
            fileJobNotificationTemplate.channelTemplate,
            ftpServerServiceNotificationTemplate.channelTemplate
        ).map { it.create(application) }
    )
}

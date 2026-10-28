/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.app.applicationScope
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.WakeWifiLock
import me.zhanghai.android.files.util.showToast

class FtpServerService : Service() {
    private var state = State.STOPPED
        set(value) {
            field = value
            _stateFlow.value = value
        }

    private lateinit var wakeWifiLock: WakeWifiLock

    private lateinit var notification: FtpServerNotification

    // Starting and stopping run one at a time, in order. They run in the application scope because
    // stopping must still complete after this service is destroyed.
    private val serverDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var server: FtpServer? = null

    override fun onCreate() {
        super.onCreate()

        wakeWifiLock = WakeWifiLock(FtpServerService::class.java.simpleName)
        notification = FtpServerNotification(this)
        executeStart()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // Called when data sync foreground services have run for their daily time limit.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

        executeStop()
    }

    private fun executeStart() {
        if (state == State.STARTING || state == State.RUNNING) {
            return
        }
        wakeWifiLock.isAcquired = true
        notification.startForeground()
        state = State.STARTING
        applicationScope.launch {
            val isStarted = try {
                withContext(serverDispatcher) { doStart() }
            } catch (e: Exception) {
                onStartError(e)
                return@launch
            }
            if (isStarted) {
                state = State.RUNNING
            }
        }
    }

    private fun onStartError(exception: Exception) {
        state = State.STOPPED
        showToast(exception.toString())
        notification.stopForeground()
        wakeWifiLock.isAcquired = false
        stopSelf()
    }

    private fun executeStop() {
        if (state == State.STOPPING || state == State.STOPPED) {
            return
        }
        state = State.STOPPING
        applicationScope.launch {
            if (withContext(serverDispatcher) { doStop() }) {
                state = State.STOPPED
            }
        }
        notification.stopForeground()
        wakeWifiLock.isAcquired = false
    }

    @WorkerThread
    private fun doStart(): Boolean {
        if (server != null) {
            return false
        }
        val username: String?
        val password: String?
        if (Settings.FTP_SERVER_ANONYMOUS_LOGIN.value) {
            username = USERNAME_ANONYMOUS
            password = null
        } else {
            username = Settings.FTP_SERVER_USERNAME.value
            password = Settings.FTP_SERVER_PASSWORD.value
        }
        val port = Settings.FTP_SERVER_PORT.value
        val homeDirectory = Settings.FTP_SERVER_HOME_DIRECTORY.value
        val writable = Settings.FTP_SERVER_WRITABLE.value
        val server = FtpServer(username, password, port, homeDirectory, writable)
        this.server = server
        try {
            server.start()
        } catch (e: Exception) {
            e.printStackTrace()
            this.server = null
            throw e
        }
        return true
    }

    @WorkerThread
    private fun doStop(): Boolean {
        val server = server ?: return false
        server.stop()
        this.server = null
        return true
    }

    companion object {
        const val USERNAME_ANONYMOUS = "anonymous"

        private val _stateFlow = MutableStateFlow(State.STOPPED)
        val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, FtpServerService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FtpServerService::class.java))
        }

        fun toggle(context: Context) {
            when (_stateFlow.value) {
                State.STARTING, State.STOPPING -> {}
                State.RUNNING -> stop(context)
                State.STOPPED -> start(context)
            }
        }
    }

    enum class State {
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED
    }
}

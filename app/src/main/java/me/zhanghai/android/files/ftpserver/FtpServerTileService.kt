/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.zhanghai.android.files.app.applicationScope
import me.zhanghai.android.files.compat.doWithStartForegroundServiceAllowed

class FtpServerTileService : TileService() {
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()

        stateJob?.cancel()
        stateJob = applicationScope.launch {
            FtpServerService.stateFlow.collect { onFtpServerStateChanged(it) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()

        stateJob?.cancel()
        stateJob = null
    }

    private fun onFtpServerStateChanged(state: FtpServerService.State) {
        val tile = qsTile
        when (state) {
            FtpServerService.State.STARTING,
            FtpServerService.State.RUNNING -> tile.state = Tile.STATE_ACTIVE
            FtpServerService.State.STOPPING -> tile.state = Tile.STATE_UNAVAILABLE
            FtpServerService.State.STOPPED -> tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (isLocked) {
            unlockAndRun { toggle() }
        } else {
            toggle()
        }
    }

    private fun toggle() {
        doWithStartForegroundServiceAllowed { FtpServerService.toggle(this) }
    }
}

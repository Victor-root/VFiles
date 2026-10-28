/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import jcifs.CIFSContext
import jcifs.CIFSException
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.app.createJcifsProperties
import me.zhanghai.android.files.util.getLocalAddress
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Executors

private const val SCAN_TIMEOUT_MILLIS = 1000

/** Finds the SMB servers on the LAN, each one as soon as it answers. */
fun scanLanSmbServers(): Flow<LanSmbServer> =
    channelFlow {
        val context = createScanContext()
        try {
            // Every address of the subnet is queried at once, since each one that has no server
            // only answers with a timeout, and each query blocks a thread meanwhile.
            Executors.newCachedThreadPool().asCoroutineDispatcher().use { dispatcher ->
                withContext(dispatcher) {
                    launch { sendServersByComputerBrowserService(context) }
                    launch { sendServersByScanningSubnet(context) }
                }
            }
        } finally {
            try {
                context.close()
            } catch (e: CIFSException) {
                e.printStackTrace()
            }
        }
    }
        .flowOn(Dispatchers.IO)

// Servers on the LAN answer in milliseconds, so waiting as long as for connecting to one would
// only make the scan slower.
private fun createScanContext(): CIFSContext =
    BaseContext(
        PropertyConfiguration(
            createJcifsProperties().apply {
                setProperty("jcifs.netbios.retryTimeout", SCAN_TIMEOUT_MILLIS.toString())
                setProperty("jcifs.smb.client.connTimeout", SCAN_TIMEOUT_MILLIS.toString())
                setProperty("jcifs.smb.client.soTimeout", SCAN_TIMEOUT_MILLIS.toString())
                setProperty(
                    "jcifs.smb.client.responseTimeout", SCAN_TIMEOUT_MILLIS.toString()
                )
            }
        )
    )

// If a computer running recent Windows 10 is elected the master browser, it won't actually
// provide the service to others (jCIFS-NG NetServerEnumIterator gets
// ERROR_SERVICE_NOT_INSTALLED), as SMBv1 has been disabled. Windows now uses WS-Discovery, but
// it doesn't have a good standalone Java implementation and Samba doesn't support it.
// https://social.technet.microsoft.com/Forums/en-US/bd0af6aa-51ec-477a-8c81-888a4e60bd94/master-browser-service-broken-after-creator-update#2c6b9e65-da8a-4e41-a2cb-db086443ef87
// https://docs.microsoft.com/en-nz/windows-server/storage/file-server/troubleshoot/smbv1-not-installed-by-default-in-windows
private suspend fun ProducerScope<LanSmbServer>.sendServersByComputerBrowserService(
    context: CIFSContext
) {
    val lan = SmbFile("smb://", context)
    val domains = try {
        runInterruptible { lan.listFiles() }
    } catch (e: SmbException) {
        e.printStackTrace()
        return
    }
    val nameServiceClient = context.nameServiceClient
    coroutineScope {
        for (domain in domains) {
            launch {
                val servers = try {
                    runInterruptible { domain.listFiles() }
                } catch (e: SmbException) {
                    e.printStackTrace()
                    return@launch
                }
                for (server in servers) {
                    launch {
                        // Drop the trailing slash
                        val host = server.name.dropLast(1)
                        val address = try {
                            runInterruptible { nameServiceClient.getByName(host).toInetAddress() }
                        } catch (e: UnknownHostException) {
                            e.printStackTrace()
                            return@launch
                        }
                        send(LanSmbServer(host, address))
                    }
                }
            }
        }
    }
}

private suspend fun ProducerScope<LanSmbServer>.sendServersByScanningSubnet(
    context: CIFSContext
) {
    val localAddress = InetAddress::class.getLocalAddress()
    if (localAddress !is Inet4Address || !localAddress.isSiteLocalAddress) {
        return
    }
    val nameServiceClient = context.nameServiceClient
    coroutineScope {
        for (address in localAddress.getSubnetAddresses()) {
            launch {
                val nbtAddresses = try {
                    runInterruptible { nameServiceClient.getNbtAllByAddress(address.hostAddress) }
                } catch (e: UnknownHostException) {
                    e.printStackTrace()
                    return@launch
                }
                val host = nbtAddresses.firstOrNull()?.hostName ?: return@launch
                send(LanSmbServer(host, address))
            }
        }
    }
}

private fun Inet4Address.getSubnetAddresses(): Sequence<Inet4Address> =
    sequence {
        val addressBytes = address
        for (i in 0..99) {
            for (j in 0..2) {
                val lastBit = 100 * j + i
                if (lastBit > 255) {
                    continue
                }
                addressBytes[3] = lastBit.toByte()
                yield(InetAddress.getByAddress(addressBytes) as Inet4Address)
            }
        }
    }

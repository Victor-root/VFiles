/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class LanSmbServerScannerTest {
    @Test
    fun scansEachAddressOfTheSubnetExceptNetworkAndBroadcast() {
        val localAddress = InetAddress.getByName("192.168.1.42") as Inet4Address
        val addresses = localAddress.getSubnetAddresses().map { it.hostAddress }.toList()
        assertEquals(254, addresses.size)
        assertEquals((1..254).map { "192.168.1.$it" }.toSet(), addresses.toSet())
        assertFalse("192.168.1.0" in addresses)
        assertFalse("192.168.1.255" in addresses)
    }
}

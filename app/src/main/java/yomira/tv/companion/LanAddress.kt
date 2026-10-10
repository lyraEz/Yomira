package yomira.tv.companion

import java.net.Inet4Address
import java.net.NetworkInterface

/** Finds the IPv4 address other devices on the local network can reach this one at. */
object LanAddress {

    fun find(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { iface ->
                when {
                    iface.name.startsWith("wlan") -> 0
                    iface.name.startsWith("eth") -> 1
                    else -> 2
                }
            }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}

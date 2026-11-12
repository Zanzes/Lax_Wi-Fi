package com.laxwifi.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

private fun InetAddress.plain(): String = (hostAddress ?: "").substringBefore('%')

fun LinkProperties.toFingerprint(): Fingerprint {
    var gateway4 = ""
    var gateway6 = ""
    for (route in routes) {
        if (!route.isDefaultRoute) continue
        val gw = route.gateway ?: continue
        if (gw.isAnyLocalAddress) continue
        when (gw) {
            is Inet4Address -> gateway4 = gw.plain()
            is Inet6Address -> gateway6 = gw.plain()
        }
    }

    var subnet = ""
    val v4 = linkAddresses.firstOrNull { it.address is Inet4Address }
    if (v4 != null) {
        val bytes = v4.address.address
        val prefix = v4.prefixLength
        for (i in bytes.indices) {
            val bits = (prefix - i * 8).coerceIn(0, 8)
            val mask = (0xFF shl (8 - bits)) and 0xFF
            bytes[i] = (bytes[i].toInt() and mask).toByte()
        }
        subnet = InetAddress.getByAddress(bytes).plain() + "/" + prefix
    }

    return Fingerprint(
        gateway4 = gateway4,
        gateway6 = gateway6,
        subnet = subnet,
        dns = dnsServers.map { it.plain() }.sorted(),
        domain = domains ?: "",
    )
}

/** Fingerprint of the Wi-Fi network the phone is on right now, or null when not on Wi-Fi. */
fun currentWifiFingerprint(context: Context): Fingerprint? {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    @Suppress("DEPRECATION")
    val wifi = cm.allNetworks.firstOrNull { network ->
        val caps = cm.getNetworkCapabilities(network)
        caps != null &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    } ?: return null
    return cm.getLinkProperties(wifi)?.toFingerprint()?.takeUnless { it.isEmpty }
}

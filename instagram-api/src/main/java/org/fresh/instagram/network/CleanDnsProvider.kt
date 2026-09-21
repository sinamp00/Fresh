package org.fresh.instagram.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Resilient DNS provider that bypasses Iranian DNS poisoning and hijacking.
 * Falls back to verified clean Anycast IP addresses for Instagram endpoints.
 */
class CleanDnsProvider(
    private val directDns: Dns = Dns.SYSTEM,
    private val fallbackDns: Dns? = null
) : Dns {

    companion object {
        // Known Iranian DNS poisoning redirect IPs (MCI, Irancell, Shatel, etc.)
        private val POISONED_IPS = setOf(
            "10.10.34.34",
            "10.10.34.35",
            "10.10.34.36",
            "10.10.34.37",
            "10.10.34.38"
        )

        // Clean Anycast Cloudflare / Meta endpoints
        private val CLEAN_META_IPS = mapOf(
            "i.instagram.com" to listOf(
                "157.240.241.174",
                "157.240.22.174",
                "31.13.72.174",
                "157.240.1.63",
                "157.240.199.63"
            ),
            "graph.instagram.com" to listOf(
                "157.240.241.174",
                "157.240.22.174"
            ),
            "instagram.com" to listOf(
                "157.240.241.174",
                "157.240.22.174"
            ),
            "www.instagram.com" to listOf(
                "157.240.241.174",
                "157.240.22.174"
            )
        )
    }

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            val resolved = directDns.lookup(hostname)
            // Filter out poisoned Iranian ISP redirects
            val filtered = resolved.filter { addr ->
                val ip = addr.hostAddress
                ip == null || !POISONED_IPS.contains(ip)
            }
            if (filtered.isNotEmpty()) {
                return filtered
            }
        } catch (_: Exception) {
            // System DNS resolution failed or blocked
        }

        // Try clean Anycast Meta addresses for Instagram hosts
        val cleanIps = CLEAN_META_IPS[hostname.lowercase()]
        if (!cleanIps.isNullOrEmpty()) {
            return cleanIps.mapNotNull {
                try {
                    InetAddress.getByName(it)
                } catch (_: Exception) {
                    null
                }
            }
        }

        // Try fallback DNS if provided
        fallbackDns?.let {
            try {
                return it.lookup(hostname)
            } catch (_: Exception) {}
        }

        throw UnknownHostException("Unable to resolve clean IP address for: $hostname")
    }
}

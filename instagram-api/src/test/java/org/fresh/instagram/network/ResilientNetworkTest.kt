package org.fresh.instagram.network

import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Proxy

class ResilientNetworkTest {

    @Test
    fun testFragmentingOutputStreamSplitsTlsClientHello() {
        val underlyingStream = ByteArrayOutputStream()
        val fragmentingStream = FragmentingOutputStream(underlyingStream, splitChunkSize = 16, delayMs = 1L)

        // Mock TLS Handshake packet (0x16, 0x03, 0x03, length ...)
        val mockClientHello = ByteArray(100) { i -> (i % 256).toByte() }
        mockClientHello[0] = 0x16.toByte() // Handshake
        mockClientHello[1] = 0x03.toByte() // TLS 1.x
        mockClientHello[2] = 0x03.toByte()

        fragmentingStream.write(mockClientHello)
        fragmentingStream.flush()

        val written = underlyingStream.toByteArray()
        assertEquals(100, written.size)
        assertArrayEquals(mockClientHello, written)
    }

    @Test
    fun testCleanDnsFiltersPoisonedIps() {
        val mockPoisonedDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return listOf(
                    InetAddress.getByName("10.10.34.34"), // Poisoned Iranian redirect
                    InetAddress.getByName("10.10.34.35")
                )
            }
        }

        val cleanDns = CleanDnsProvider(directDns = mockPoisonedDns)
        val resolved = cleanDns.lookup("i.instagram.com")

        assertFalse(resolved.isEmpty())
        for (addr in resolved) {
            assertNotEquals("10.10.34.34", addr.hostAddress)
            assertNotEquals("10.10.34.35", addr.hostAddress)
        }
    }

    @Test
    fun testTelegramProxyBridgeConfiguration() {
        val proxyConfig = FreshProxyConfig(
            enabled = true,
            type = Proxy.Type.SOCKS,
            host = "127.0.0.1",
            port = 10808
        )

        TelegramProxyBridge.updateProxy(proxyConfig)
        assertEquals(proxyConfig, TelegramProxyBridge.activeConfig)

        val javaProxy = proxyConfig.toJavaProxy()
        assertEquals(Proxy.Type.SOCKS, javaProxy.type())
        assertNotNull(javaProxy.address())

        // Test disabled proxy returns NO_PROXY
        val disabledConfig = FreshProxyConfig(enabled = false)
        assertEquals(Proxy.NO_PROXY, disabledConfig.toJavaProxy())
    }
}

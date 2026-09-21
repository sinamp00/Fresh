package org.fresh.instagram.device

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class InstagramDeviceTest {

    @Test
    fun testGenerateDeviceId() {
        val deviceId = InstagramDevice.generateDeviceId(UUID.randomUUID().toString())
        assertTrue(deviceId.startsWith("android-"))
        val hexPart = deviceId.removePrefix("android-")
        assertEquals(16, hexPart.length)
        assertTrue(hexPart.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun testGenerateDeviceFingerprint() {
        val device = InstagramDevice.generate()
        assertNotNull(device)
        assertTrue(device.deviceId.startsWith("android-"))
        assertEquals(36, device.uuid.length) // Standard UUID length
        assertEquals(36, device.phoneId.length)
        assertEquals(36, device.adid.length)
        assertEquals(36, device.clientSessionId.length)

        val oldSessionId = device.clientSessionId
        device.refreshClientSessionId()
        assertNotEquals(oldSessionId, device.clientSessionId)
        assertEquals(36, device.clientSessionId.length)
    }
}

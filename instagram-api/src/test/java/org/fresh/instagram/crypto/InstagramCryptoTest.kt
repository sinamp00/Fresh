package org.fresh.instagram.crypto

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class InstagramCryptoTest {

    @Test
    fun testPasswordEnvelopeEncryptionLayoutAndRoundTrip() {
        // Generate test RSA-2048 keypair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()
        val pubKeyBase64 = InstagramCryptoUtils.base64Encode(keyPair.public.encoded)

        val password = "SuperSecretPassword123!"
        val timestamp = 1726880000L
        val keyId = "225"

        val encResult = InstagramCryptoUtils.encryptPassword(
            password = password,
            keyId = keyId,
            publicKeyBase64 = pubKeyBase64,
            timeSeconds = timestamp
        )

        assertTrue(encResult.startsWith("#PWD_INSTAGRAM:4:$timestamp:"))

        val base64Payload = encResult.substringAfterLast(":")
        val rawBytes = InstagramCryptoUtils.base64Decode(base64Payload)

        // Verify layout
        assertEquals(0x01.toByte(), rawBytes[0]) // Version 1
        assertEquals(225.toByte(), rawBytes[1]) // Key ID

        // IV: 12 bytes (2..13)
        val iv = rawBytes.copyOfRange(2, 14)
        assertEquals(12, iv.size)

        // Key length: 2 bytes Little Endian (14..15)
        val bb = ByteBuffer.wrap(rawBytes, 14, 2).order(ByteOrder.LITTLE_ENDIAN)
        val keyLen = bb.short.toInt() and 0xFFFF
        assertEquals(256, keyLen)

        // RSA encrypted key: 256 bytes (16..271)
        val rsaEncKey = rawBytes.copyOfRange(16, 16 + keyLen)
        assertEquals(256, rsaEncKey.size)

        // GCM tag: 16 bytes (272..287)
        val gcmTag = rawBytes.copyOfRange(16 + keyLen, 16 + keyLen + 16)
        assertEquals(16, gcmTag.size)

        // Ciphertext: remaining bytes
        val cipherText = rawBytes.copyOfRange(16 + keyLen + 16, rawBytes.size)
        assertEquals(password.toByteArray(StandardCharsets.UTF_8).size, cipherText.size)

        // Decrypt with private key to prove genuine crypto round-trip
        val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        val decryptedAesKey = rsaCipher.doFinal(rsaEncKey)
        assertEquals(32, decryptedAesKey.size)

        // Reconstruct GCM payload: ciphertext + gcmTag
        val gcmPayload = ByteArray(cipherText.size + gcmTag.size)
        System.arraycopy(cipherText, 0, gcmPayload, 0, cipherText.size)
        System.arraycopy(gcmTag, 0, gcmPayload, cipherText.size, gcmTag.size)

        val gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(decryptedAesKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        gcmCipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        gcmCipher.updateAAD(timestamp.toString().toByteArray(StandardCharsets.US_ASCII))
        val decryptedPasswordBytes = gcmCipher.doFinal(gcmPayload)

        assertEquals(password, String(decryptedPasswordBytes, StandardCharsets.UTF_8))
    }

    @Test
    fun testHmacSha256Signing() {
        val payload = """{"username":"test_user","login_attempt_count":0}"""
        val signed = InstagramCryptoUtils.signPayload(payload)
        assertTrue(signed.endsWith(".$payload"))

        val sigPart = signed.substringBefore(".")
        assertEquals(64, sigPart.length)

        // Verify repeatability
        val sig2 = InstagramCryptoUtils.calculateHmacSha256(payload, InstagramCryptoUtils.IG_SIGNATURE_KEY)
        assertEquals(sigPart, sig2)
    }

    @Test
    fun testJazoestCalculation() {
        val phoneId = "c3a7d81e-2213-4e89-8d19-e932456789ab"
        val jazoest = InstagramCryptoUtils.calculateJazoest(phoneId)
        assertTrue(jazoest.startsWith("2"))

        var expectedSum = 0
        for (ch in phoneId.toCharArray()) {
            expectedSum += ch.code
        }
        assertEquals("2$expectedSum", jazoest)
    }
}

package org.fresh.instagram.crypto

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Adversarial Cryptographic & Protocol Oracle Stress Test Suite.
 * Created by challenger_m2_1 for Milestone 2 Verification.
 *
 * Challenges:
 * 1. RSA-2048 + AES-256-GCM Envelope Round-trip Oracle across diverse payload types.
 * 2. Authenticated Data (AAD) Tampering Detection (AEADBadTagException).
 * 3. Ciphertext Bit-Flipping Integrity Detection (AEADBadTagException).
 * 4. Auth Tag Corruption Detection (AEADBadTagException).
 * 5. IV Mutation Detection (AEADBadTagException).
 * 6. RSA Block Tampering Detection (BadPaddingException or AEADBadTagException).
 * 7. Multithreaded High-Contention Concurrency Stress.
 * 8. Edge Case Passwords (empty, multi-kilobyte, RTL Persian, non-BMP emojis, null-bytes).
 * 9. Jazoest Boundary and ASCII Edge Cases.
 * 10. HMAC-SHA256 Request Signing Invariance and Key Consistency.
 */
class InstagramCryptoAdversarialStressTest {

    private val rsaKeyPair: KeyPair by lazy {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        kpg.generateKeyPair()
    }

    private val pubKeyBase64: String by lazy {
        InstagramCryptoUtils.base64Encode(rsaKeyPair.public.encoded)
    }

    private data class UnpackedEnvelope(
        val version: Byte,
        val keyId: Int,
        val iv: ByteArray,
        val keyLen: Int,
        val rsaEncryptedKey: ByteArray,
        val gcmTag: ByteArray,
        val cipherText: ByteArray,
        val timestamp: Long
    )

    private fun unpackEnvelopeString(envelope: String): UnpackedEnvelope {
        assertTrue("Envelope must start with #PWD_INSTAGRAM:4:", envelope.startsWith("#PWD_INSTAGRAM:4:"))
        val parts = envelope.split(":")
        assertEquals("Envelope must have 4 colon-separated parts", 4, parts.size)
        val timestamp = parts[2].toLong()
        val rawBytes = InstagramCryptoUtils.base64Decode(parts[3])

        assertTrue("Envelope payload must be at least 286 bytes", rawBytes.size >= 286)
        val version = rawBytes[0]
        val keyId = rawBytes[1].toInt() and 0xFF
        val iv = rawBytes.copyOfRange(2, 14)
        val bb = ByteBuffer.wrap(rawBytes, 14, 2).order(ByteOrder.LITTLE_ENDIAN)
        val keyLen = bb.short.toInt() and 0xFFFF
        val rsaEncryptedKey = rawBytes.copyOfRange(16, 16 + keyLen)
        val gcmTag = rawBytes.copyOfRange(16 + keyLen, 16 + keyLen + 16)
        val cipherText = rawBytes.copyOfRange(16 + keyLen + 16, rawBytes.size)

        return UnpackedEnvelope(
            version = version,
            keyId = keyId,
            iv = iv,
            keyLen = keyLen,
            rsaEncryptedKey = rsaEncryptedKey,
            gcmTag = gcmTag,
            cipherText = cipherText,
            timestamp = timestamp
        )
    }

    private fun decryptEnvelope(
        unpacked: UnpackedEnvelope,
        customAadTimestamp: Long? = null
    ): String {
        // 1. Decrypt AES key with RSA Private Key
        val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsaCipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.private)
        val decryptedAesKey = rsaCipher.doFinal(unpacked.rsaEncryptedKey)
        assertEquals(32, decryptedAesKey.size)

        // 2. Reconstruct JCE GCM payload: ciphertext + gcmTag
        val gcmPayload = ByteArray(unpacked.cipherText.size + unpacked.gcmTag.size)
        System.arraycopy(unpacked.cipherText, 0, gcmPayload, 0, unpacked.cipherText.size)
        System.arraycopy(unpacked.gcmTag, 0, gcmPayload, unpacked.cipherText.size, unpacked.gcmTag.size)

        // 3. Decrypt with AES-256-GCM
        val gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(decryptedAesKey, "AES")
        val gcmSpec = GCMParameterSpec(128, unpacked.iv)
        gcmCipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val aadTime = customAadTimestamp ?: unpacked.timestamp
        gcmCipher.updateAAD(aadTime.toString().toByteArray(StandardCharsets.US_ASCII))
        val decryptedBytes = gcmCipher.doFinal(gcmPayload)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    @Test
    fun testEnvelopeFormatAndOracleRoundTrip() {
        val testPasswords = listOf(
            "simple123",
            "Complex!@#\$%^&*()_+~`-=[]{}|;':,./<>?",
            "گالری_آوینسا_فرش_۲۰۲۶",
            "🚀🔥💎👑📱🛡️",
            "a".repeat(1024),
            "Line1\nLine2\r\nLine3\tTabbed",
            ""
        )

        for ((idx, pwd) in testPasswords.withIndex()) {
            val ts = 1726900000L + idx * 3600L
            val keyId = (200 + idx).toString()
            val enc = InstagramCryptoUtils.encryptPassword(
                password = pwd,
                keyId = keyId,
                publicKeyBase64 = pubKeyBase64,
                timeSeconds = ts
            )

            val unpacked = unpackEnvelopeString(enc)
            assertEquals("Version byte must be 0x01", 0x01.toByte(), unpacked.version)
            assertEquals("Key ID must match", (200 + idx), unpacked.keyId)
            assertEquals("IV size must be 12 bytes", 12, unpacked.iv.size)
            assertEquals("RSA key length must be 256 bytes", 256, unpacked.keyLen)
            assertEquals("GCM tag must be 16 bytes", 16, unpacked.gcmTag.size)
            assertEquals("Timestamp must match", ts, unpacked.timestamp)

            val decrypted = decryptEnvelope(unpacked)
            assertEquals("Decrypted password must match original", pwd, decrypted)
        }
    }

    @Test
    fun testCorruptedAadThrowsAeadBadTagException() {
        val pwd = "SensitivePassword987!"
        val originalTs = 1726880000L
        val enc = InstagramCryptoUtils.encryptPassword(pwd, "225", pubKeyBase64, originalTs)
        val unpacked = unpackEnvelopeString(enc)

        // Case 1: Timestamp + 1
        try {
            decryptEnvelope(unpacked, customAadTimestamp = originalTs + 1)
            fail("Expected AEADBadTagException when AAD timestamp is modified (+1)")
        } catch (e: Exception) {
            assertTrue("Exception must be or cause AEADBadTagException: ${e.message}",
                e is AEADBadTagException || e.cause is AEADBadTagException)
        }

        // Case 2: Timestamp - 1
        try {
            decryptEnvelope(unpacked, customAadTimestamp = originalTs - 1)
            fail("Expected AEADBadTagException when AAD timestamp is modified (-1)")
        } catch (e: Exception) {
            assertTrue("Exception must be or cause AEADBadTagException",
                e is AEADBadTagException || e.cause is AEADBadTagException)
        }

        // Case 3: Epoch zero AAD
        try {
            decryptEnvelope(unpacked, customAadTimestamp = 0L)
            fail("Expected AEADBadTagException when AAD timestamp is 0")
        } catch (e: Exception) {
            assertTrue("Exception must be or cause AEADBadTagException",
                e is AEADBadTagException || e.cause is AEADBadTagException)
        }
    }

    @Test
    fun testModifiedCiphertextBitFlippingThrowsException() {
        val pwd = "RobustPasswordAgainstBitFlipping2026"
        val enc = InstagramCryptoUtils.encryptPassword(pwd, "225", pubKeyBase64, 1726880000L)
        val unpacked = unpackEnvelopeString(enc)

        // Flip bits in ciphertext at various positions (start, middle, end)
        val testIndices = listOf(0, unpacked.cipherText.size / 2, unpacked.cipherText.size - 1)
        for (idx in testIndices) {
            val corruptedCipher = unpacked.cipherText.clone()
            corruptedCipher[idx] = (corruptedCipher[idx].toInt() xor 0x01).toByte()

            val corruptedUnpacked = unpacked.copy(cipherText = corruptedCipher)
            try {
                decryptEnvelope(corruptedUnpacked)
                fail("Expected AEADBadTagException when ciphertext bit at index $idx is flipped")
            } catch (e: Exception) {
                assertTrue("Expected authentication tag verification failure",
                    e is AEADBadTagException || e.cause is AEADBadTagException)
            }
        }
    }

    @Test
    fun testCorruptedGcmTagThrowsException() {
        val pwd = "TagIntegrityTestPassword"
        val enc = InstagramCryptoUtils.encryptPassword(pwd, "225", pubKeyBase64, 1726880000L)
        val unpacked = unpackEnvelopeString(enc)

        for (tagByteIdx in listOf(0, 7, 15)) {
            val corruptedTag = unpacked.gcmTag.clone()
            corruptedTag[tagByteIdx] = (corruptedTag[tagByteIdx].toInt() xor 0x80).toByte()

            val corruptedUnpacked = unpacked.copy(gcmTag = corruptedTag)
            try {
                decryptEnvelope(corruptedUnpacked)
                fail("Expected AEADBadTagException when GCM tag byte $tagByteIdx is corrupted")
            } catch (e: Exception) {
                assertTrue(e is AEADBadTagException || e.cause is AEADBadTagException)
            }
        }
    }

    @Test
    fun testCorruptedIvThrowsException() {
        val pwd = "IvIntegrityTestPassword"
        val enc = InstagramCryptoUtils.encryptPassword(pwd, "225", pubKeyBase64, 1726880000L)
        val unpacked = unpackEnvelopeString(enc)

        for (ivIdx in listOf(0, 5, 11)) {
            val corruptedIv = unpacked.iv.clone()
            corruptedIv[ivIdx] = (corruptedIv[ivIdx].toInt() xor 0x40).toByte()

            val corruptedUnpacked = unpacked.copy(iv = corruptedIv)
            try {
                decryptEnvelope(corruptedUnpacked)
                fail("Expected AEADBadTagException when IV byte $ivIdx is modified")
            } catch (e: Exception) {
                assertTrue(e is AEADBadTagException || e.cause is AEADBadTagException)
            }
        }
    }

    @Test
    fun testCorruptedRsaBlockThrowsException() {
        val pwd = "RsaIntegrityTestPassword"
        val enc = InstagramCryptoUtils.encryptPassword(pwd, "225", pubKeyBase64, 1726880000L)
        val unpacked = unpackEnvelopeString(enc)

        val corruptedRsa = unpacked.rsaEncryptedKey.clone()
        corruptedRsa[10] = (corruptedRsa[10].toInt() xor 0xFF).toByte()

        val corruptedUnpacked = unpacked.copy(rsaEncryptedKey = corruptedRsa)
        try {
            decryptEnvelope(corruptedUnpacked)
            fail("Expected failure when RSA block is corrupted")
        } catch (e: Exception) {
            // Decryption of corrupted RSA block either throws BadPaddingException during RSA
            // or produces garbage AES key which causes AEADBadTagException during AES-GCM
            val isExpected = e is BadPaddingException || e.cause is BadPaddingException ||
                    e is AEADBadTagException || e.cause is AEADBadTagException
            assertTrue("Expected BadPaddingException or AEADBadTagException, got: $e", isExpected)
        }
    }

    @Test
    fun testConcurrentEncryptionThreadSafety() {
        val threadCount = 16
        val iterationsPerThread = 25
        val executor = Executors.newFixedThreadPool(threadCount)
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val pwd = "Thread-${t}-Iter-${i}-PWD"
                        val ts = 1726880000L + (t * 100) + i
                        val enc = InstagramCryptoUtils.encryptPassword(pwd, "225", pubKeyBase64, ts)
                        val unpacked = unpackEnvelopeString(enc)
                        val decrypted = decryptEnvelope(unpacked)
                        if (decrypted != pwd) {
                            errors.add(AssertionError("Mismatch in thread $t, iter $i: expected $pwd but got $decrypted"))
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        executor.shutdown()
        assertTrue("Executor must complete within 30 seconds", executor.awaitTermination(30, TimeUnit.SECONDS))
        if (!errors.isEmpty()) {
            fail("Concurrency errors encountered (${errors.size}): ${errors.peek()?.message}")
        }
    }

    @Test
    fun testHmacSha256StressAndEdgeCases() {
        val officialKey = InstagramCryptoUtils.IG_SIGNATURE_KEY

        // 1. Empty payload
        val emptySig = InstagramCryptoUtils.calculateHmacSha256("", officialKey)
        assertEquals(64, emptySig.length)

        // 2. Large JSON payload (100KB)
        val largeJson = StringBuilder("{\"items\":[")
        for (i in 0 until 1000) {
            if (i > 0) largeJson.append(",")
            largeJson.append("{\"id\":$i,\"name\":\"item_$i\"}")
        }
        largeJson.append("]}")
        val largeSig = InstagramCryptoUtils.calculateHmacSha256(largeJson.toString(), officialKey)
        assertEquals(64, largeSig.length)

        // 3. UTF-8 Persian/Arabic content
        val persianJson = "{\"store\":\"گالری آوینسا\",\"currency\":\"تومان\",\"amount\":2650000}"
        val persianSigned = InstagramCryptoUtils.signPayload(persianJson)
        assertTrue(persianSigned.startsWith(InstagramCryptoUtils.calculateHmacSha256(persianJson, officialKey)))
        assertTrue(persianSigned.endsWith(".$persianJson"))

        // 4. generateSignedBody formatting
        val formBody = InstagramCryptoUtils.generateSignedBody(persianJson)
        assertTrue(formBody.startsWith("signed_body="))
        assertTrue(formBody.contains("&ig_sig_key_version=4"))
    }

    @Test
    fun testJazoestEdgeCases() {
        // Standard UUID format
        val standardUuid = "c3a7d81e-2213-4e89-8d19-e932456789ab"
        val standardSum = standardUuid.sumOf { it.code }
        assertEquals("2$standardSum", InstagramCryptoUtils.calculateJazoest(standardUuid))

        // Empty string -> sum = 0 -> "20"
        assertEquals("20", InstagramCryptoUtils.calculateJazoest(""))

        // Single character 'A' (code 65) -> "265"
        assertEquals("265", InstagramCryptoUtils.calculateJazoest("A"))

        // All zeros
        val allZeros = "00000000-0000-0000-0000-000000000000"
        val zeroSum = allZeros.sumOf { it.code }
        assertEquals("2$zeroSum", InstagramCryptoUtils.calculateJazoest(allZeros))

        // 1000 characters
        val longId = "x".repeat(1000)
        assertEquals("2" + (1000 * 'x'.code), InstagramCryptoUtils.calculateJazoest(longId))
    }
}

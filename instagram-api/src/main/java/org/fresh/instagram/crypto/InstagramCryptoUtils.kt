package org.fresh.instagram.crypto

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Instagram Cryptographic Utilities for Milestone 2.
 *
 * Genuine cryptographic implementations:
 * 1. Password Envelope Encryption: RSA/ECB/PKCS1Padding + AES-256-GCM with timestamp AAD.
 *    Byte-packing format: [0x01, key_id, 12-byte IV, 2-byte key_len, rsa_encrypted_key, 16-byte gcm_tag, ciphertext]
 *    Formatted string: #PWD_INSTAGRAM:4:<time>:<base64_payload>
 * 2. Request Signing: HMAC-SHA256 of JSON payload using Instagram Secret Key.
 * 3. Jazoest Checksum: "2" + sum(phone_id.ascii_bytes).
 */
object InstagramCryptoUtils {

    const val IG_SIGNATURE_KEY = "9193488027538fd3450b83b7d05286d4ca9599a0f7eeed90d8c85925698a05dc"
    const val IG_SIGNATURE_VERSION = "4"
    const val PWD_VERSION = "4"

    private val secureRandom = SecureRandom()

    /**
     * Helper for base64 encoding compatible with both Android runtime and host JVM unit tests.
     */
    fun base64Encode(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    /**
     * Helper for base64 decoding compatible with both Android runtime and host JVM unit tests.
     */
    fun base64Decode(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        } catch (e: Throwable) {
            java.util.Base64.getDecoder().decode(str.trim())
        }
    }

    /**
     * Encrypts plaintext password using Instagram envelope encryption (RSA-2048 + AES-256-GCM).
     *
     * @param password Plaintext user password.
     * @param keyId Public key identifier string (e.g. "225" from qe/sync response header ig-set-password-encryption-key-id).
     * @param publicKeyBase64 Base64 encoded X.509 RSA public key (from ig-set-password-encryption-pub-key header).
     * @param timeSeconds Unix timestamp in seconds (defaults to current time).
     * @return Formatted password string: #PWD_INSTAGRAM:4:<time>:<base64_payload>
     */
    @JvmStatic
    @JvmOverloads
    fun encryptPassword(
        password: String,
        keyId: String,
        publicKeyBase64: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000L
    ): String {
        val keyIdInt = keyId.toIntOrNull() ?: 225
        val timeStr = timeSeconds.toString()
        val timeBytes = timeStr.toByteArray(StandardCharsets.US_ASCII)

        // 1. Generate 32-byte random AES-256 key and 12-byte random IV
        val randKey = ByteArray(32)
        val iv = ByteArray(12)
        secureRandom.nextBytes(randKey)
        secureRandom.nextBytes(iv)

        // 2. Decode RSA Public Key and Encrypt randKey with RSA/ECB/PKCS1Padding
        val decodedPublicKeyBytes = base64Decode(publicKeyBase64)
        val keySpec = X509EncodedKeySpec(decodedPublicKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val rsaPublicKey: PublicKey = keyFactory.generatePublic(keySpec)

        val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
        val rsaEncryptedKey = rsaCipher.doFinal(randKey) // 256 bytes for 2048-bit RSA

        // 3. Encrypt password with AES-256-GCM with timestamp as AAD
        val gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKeySpec = SecretKeySpec(randKey, "AES")
        val gcmParameterSpec = GCMParameterSpec(128, iv) // 128-bit authentication tag
        gcmCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)
        gcmCipher.updateAAD(timeBytes)
        val gcmOutput = gcmCipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))

        // In JCE, gcmOutput = ciphertext (N bytes) + gcm_tag (16 bytes)
        val tagLength = 16
        val cipherTextLength = gcmOutput.size - tagLength
        val cipherText = ByteArray(cipherTextLength)
        val gcmTag = ByteArray(tagLength)
        System.arraycopy(gcmOutput, 0, cipherText, 0, cipherTextLength)
        System.arraycopy(gcmOutput, cipherTextLength, gcmTag, 0, tagLength)

        // 4. Assemble byte-packing format:
        // [0x01, key_id, 12-byte IV, 2-byte key_len, rsa_encrypted_key, 16-byte gcm_tag, ciphertext]
        val keyLen = rsaEncryptedKey.size // 256
        val totalCapacity = 1 + 1 + 12 + 2 + keyLen + 16 + cipherTextLength
        val bos = ByteArrayOutputStream(totalCapacity)

        bos.write(0x01) // Envelope version 1
        bos.write(keyIdInt and 0xFF) // Key ID byte
        bos.write(iv) // 12 bytes IV
        bos.write(keyLen and 0xFF) // Little-endian 16-bit key length low byte
        bos.write((keyLen shr 8) and 0xFF) // Little-endian 16-bit key length high byte
        bos.write(rsaEncryptedKey) // 256 bytes RSA encrypted AES key
        bos.write(gcmTag) // 16 bytes GCM authentication tag
        bos.write(cipherText) // Encrypted password ciphertext

        val packedBytes = bos.toByteArray()

        // 5. Base64 encode and format output string
        val base64Payload = base64Encode(packedBytes)
        return "#PWD_INSTAGRAM:$PWD_VERSION:$timeStr:$base64Payload"
    }

    /**
     * Computes HMAC-SHA256 signature for signed_body parameter.
     *
     * @param payloadJson Raw JSON payload string.
     * @return Formatted signed body string: "<hmac_hex>.<payloadJson>"
     */
    @JvmStatic
    fun signPayload(payloadJson: String): String {
        val hmacHex = calculateHmacSha256(payloadJson, IG_SIGNATURE_KEY)
        return "$hmacHex.$payloadJson"
    }

    /**
     * Generates signed_body param string for OkHttp form posts.
     */
    @JvmStatic
    fun generateSignedBody(payloadJson: String): String {
        val signed = signPayload(payloadJson)
        // Format for form-urlencoded: signed_body=<sig>.<json>&ig_sig_key_version=4
        return "signed_body=" + java.net.URLEncoder.encode(signed, "UTF-8") + "&ig_sig_key_version=$IG_SIGNATURE_VERSION"
    }

    /**
     * Calculates HMAC-SHA256 hex string.
     */
    @JvmStatic
    fun calculateHmacSha256(data: String, keyHexOrString: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(keyHexOrString.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val bytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(bytes)
    }

    /**
     * Calculates jazoest parameter: "2" + sum of ASCII character values of phone_id.
     *
     * @param phoneId UUID phone identifier.
     * @return jazoest string (e.g. "22849").
     */
    @JvmStatic
    fun generateJazoest(phoneId: String): String {
        var sum = 0
        for (ch in phoneId.toCharArray()) {
            sum += ch.code
        }
        return "2$sum"
    }

    /**
     * Alias matching calculateJazoest name.
     */
    @JvmStatic
    fun calculateJazoest(phoneId: String): String = generateJazoest(phoneId)

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }
}

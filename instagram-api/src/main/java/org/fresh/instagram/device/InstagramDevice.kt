package org.fresh.instagram.device

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import java.util.UUID

/**
 * Instagram Device Fingerprint and Session Identity.
 *
 * Provides persistent device parameters required to prevent fraud flags
 * and checkpoint triggers on Instagram Private API endpoints.
 */
data class InstagramDevice(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("uuid") val uuid: String,
    @SerializedName("phone_id") val phoneId: String,
    @SerializedName("adid") val adid: String,
    @SerializedName("client_session_id") var clientSessionId: String,
    @SerializedName("user_agent") val userAgent: String = DEFAULT_USER_AGENT,
    @SerializedName("app_id") val appId: String = DEFAULT_APP_ID,
    @SerializedName("capabilities") val capabilities: String = DEFAULT_CAPABILITIES,
    @SerializedName("bloks_version_id") val bloksVersionId: String = DEFAULT_BLOKS_VERSION_ID
) {

    companion object {
        const val PREFS_NAME = "fresh_ig_device_vault"
        const val KEY_DEVICE_JSON = "device_fingerprint"

        const val DEFAULT_USER_AGENT =
            "Instagram 222.0.0.13.114 Android (30/11; 480dpi; 1080x2400; samsung; SM-G991B; o1s; exynos2100; en_US; 350696709)"
        const val DEFAULT_APP_ID = "567067343352427"
        const val DEFAULT_CAPABILITIES = "3brTv10="
        const val DEFAULT_BLOKS_VERSION_ID =
            "388ece79ebc0e70e87873505ed1b0ff335ae2868a978cc951b6721c41d46a30a"

        /**
         * Generates a deterministic device_id with prefix "android-" + 16 hex characters.
         */
        @JvmStatic
        fun generateDeviceId(seed: String = UUID.randomUUID().toString()): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(seed.toByteArray())
            val hex = StringBuilder()
            for (i in 0 until 8) { // 8 bytes = 16 hex characters
                hex.append(String.format("%02x", digest[i]))
            }
            return "android-$hex"
        }

        /**
         * Generates a fresh, realistic device fingerprint.
         */
        @JvmStatic
        fun generate(): InstagramDevice {
            val uuid = UUID.randomUUID().toString()
            val phoneId = UUID.randomUUID().toString()
            val adid = UUID.randomUUID().toString()
            val clientSessionId = UUID.randomUUID().toString()
            val deviceId = generateDeviceId(uuid)

            return InstagramDevice(
                deviceId = deviceId,
                uuid = uuid,
                phoneId = phoneId,
                adid = adid,
                clientSessionId = clientSessionId
            )
        }

        /**
         * Loads existing device fingerprint from persistent storage, or generates and saves one if absent.
         */
        @JvmStatic
        fun getOrCreate(context: Context): InstagramDevice {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_DEVICE_JSON, null)
            if (!json.isNullOrEmpty()) {
                try {
                    val device = Gson().fromJson(json, InstagramDevice::class.java)
                    if (device != null && device.deviceId.isNotBlank()) {
                        return device
                    }
                } catch (e: Exception) {
                    // fall through to generate fresh
                }
            }
            val newDevice = generate()
            save(context, newDevice)
            return newDevice
        }

        /**
         * Persists device fingerprint in SharedPreferences.
         */
        @JvmStatic
        fun save(context: Context, device: InstagramDevice) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = Gson().toJson(device)
            prefs.edit().putString(KEY_DEVICE_JSON, json).apply()
        }
    }

    /**
     * Refreshes client_session_id for new foreground sessions without altering permanent hardware identifiers.
     */
    fun refreshClientSessionId() {
        clientSessionId = UUID.randomUUID().toString()
    }
}

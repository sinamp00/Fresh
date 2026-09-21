package org.fresh.instagram.client

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.fresh.instagram.crypto.InstagramCryptoUtils
import org.fresh.instagram.device.InstagramDevice
import org.fresh.instagram.model.clips.ClipsResponse
import org.fresh.instagram.model.clips.ReelsTrayResponse
import org.fresh.instagram.model.clips.UserClipsResponse
import org.fresh.instagram.model.feed.TimelineFeedResponse
import org.fresh.instagram.model.session.*
import org.fresh.instagram.session.InstagramSessionManager
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * High-performance, resilient Instagram Private API Client for Milestone 2.
 *
 * Implements genuine protocol interactions:
 * - /api/v1/accounts/login/ with envelope encryption & signed_body
 * - /api/v1/accounts/two_factor_login/ (SMS & TOTP)
 * - /api/v1/challenge/ state query, method selection, and security code verification
 * - /api/v1/feed/timeline/ with cursor pagination (next_max_id)
 * - /api/v1/feed/reels_tray/
 * - /api/v1/clips/discover/
 * - /api/v1/clips/user/
 */
class InstagramApiClient @JvmOverloads constructor(
    val sessionManager: InstagramSessionManager,
    val okHttpClient: OkHttpClient = createDefaultHttpClient(sessionManager)
) {
    private val gson = Gson()
    private val jsonMediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()

    // -------------------------------------------------------------
    // Authentication Operations
    // -------------------------------------------------------------

    /**
     * Authenticates with Instagram Private API using genuine RSA/AES-GCM password encryption and HMAC-SHA256 signature.
     */
    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val uuid = sessionManager.getOrGenerateUuid()
        val deviceId = sessionManager.getOrGenerateDeviceId()
        val phoneId = sessionManager.getOrGeneratePhoneId()
        val adid = sessionManager.getOrGenerateAdid()

        // 1. Fetch encryption public key from QE sync
        val keySync = fetchPublicKeySync()
        val encPassword = InstagramCryptoUtils.encryptPassword(
            password = password,
            keyId = keySync.keyId,
            publicKeyBase64 = keySync.publicKey
        )

        // 2. Prepare payload matching official Android client
        val payload = JsonObject().apply {
            addProperty("username", username)
            addProperty("enc_password", encPassword)
            addProperty("guid", uuid)
            addProperty("phone_id", phoneId)
            addProperty("device_id", deviceId)
            addProperty("adid", adid)
            addProperty("_csrftoken", sessionManager.cookieJar.getCookieValue("csrftoken") ?: "missing")
            addProperty("google_tokens", "[]")
            addProperty("login_attempt_count", 0)
            addProperty("country_codes", "[{\"country_code\":\"1\",\"source\":\"default\"}]")
            addProperty("jazoest", InstagramCryptoUtils.calculateJazoest(phoneId))
        }

        val signedBody = InstagramCryptoUtils.generateSignedBody(payload.toString())
        val requestBody = signedBody.toRequestBody(jsonMediaType)

        val request = buildRequest("api/v1/accounts/login/")
            .post(requestBody)
            .build()

        executeAuthRequest(request, username)
    }

    /**
     * Synchronous blocking login helper for Java callers.
     */
    fun loginBlocking(username: String, password: String): LoginResult = runBlocking {
        login(username, password)
    }

    /**
     * Verifies Two-Factor authentication code (SMS or TOTP).
     *
     * @param verificationMethod "1" for SMS OTP, "2" for TOTP authenticator apps.
     */
    suspend fun verifyTwoFactor(
        username: String,
        twoFactorIdentifier: String,
        code: String,
        verificationMethod: String = "1"
    ): LoginResult = withContext(Dispatchers.IO) {
        val uuid = sessionManager.getOrGenerateUuid()
        val deviceId = sessionManager.getOrGenerateDeviceId()
        val csrf = sessionManager.cookieJar.getCookieValue("csrftoken") ?: "missing"

        val payload = JsonObject().apply {
            addProperty("username", username)
            addProperty("two_factor_identifier", twoFactorIdentifier)
            addProperty("verification_code", code)
            addProperty("verification_method", verificationMethod)
            addProperty("trust_this_device", "1")
            addProperty("guid", uuid)
            addProperty("device_id", deviceId)
            addProperty("_csrftoken", csrf)
        }

        val signedBody = InstagramCryptoUtils.generateSignedBody(payload.toString())
        val request = buildRequest("api/v1/accounts/two_factor_login/")
            .post(signedBody.toRequestBody(jsonMediaType))
            .build()

        executeAuthRequest(request, username)
    }

    fun verifyTwoFactorBlocking(
        username: String,
        twoFactorIdentifier: String,
        code: String,
        verificationMethod: String = "1"
    ): LoginResult = runBlocking {
        verifyTwoFactor(username, twoFactorIdentifier, code, verificationMethod)
    }

    // -------------------------------------------------------------
    // Challenge Resolution Operations
    // -------------------------------------------------------------

    suspend fun getChallengeState(apiPath: String): Result<ChallengeResponse> = withContext(Dispatchers.IO) {
        val cleanPath = apiPath.removePrefix("/")
        val uuid = sessionManager.getOrGenerateUuid()
        val deviceId = sessionManager.getOrGenerateDeviceId()

        val url = "$BASE_URL$cleanPath?guid=$uuid&device_id=$deviceId"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", InstagramDevice.DEFAULT_USER_AGENT)
            .addHeader("X-IG-App-ID", InstagramDevice.DEFAULT_APP_ID)
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            val state = gson.fromJson(body, ChallengeResponse::class.java)
            Result.success(state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun selectChallengeMethod(apiPath: String, choice: String): Result<ChallengeResponse> =
        withContext(Dispatchers.IO) {
            val cleanPath = apiPath.removePrefix("/")
            val formBody = FormBody.Builder()
                .add("choice", choice) // "0" SMS, "1" Email
                .build()

            val request = Request.Builder()
                .url("$BASE_URL$cleanPath")
                .addHeader("User-Agent", InstagramDevice.DEFAULT_USER_AGENT)
                .addHeader("X-IG-App-ID", InstagramDevice.DEFAULT_APP_ID)
                .post(formBody)
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                val state = gson.fromJson(body, ChallengeResponse::class.java)
                Result.success(state)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun submitChallengeSecurityCode(apiPath: String, code: String, username: String): LoginResult =
        withContext(Dispatchers.IO) {
            val cleanPath = apiPath.removePrefix("/")
            val formBody = FormBody.Builder()
                .add("security_code", code)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL$cleanPath")
                .addHeader("User-Agent", InstagramDevice.DEFAULT_USER_AGENT)
                .addHeader("X-IG-App-ID", InstagramDevice.DEFAULT_APP_ID)
                .post(formBody)
                .build()

            executeAuthRequest(request, username)
        }

    /**
     * Unified challenge resolver matching PROJECT.md interface contract.
     */
    suspend fun handleChallenge(
        checkpointUrl: String,
        choice: String? = null,
        securityCode: String? = null,
        username: String = ""
    ): ChallengeResult = withContext(Dispatchers.IO) {
        val cleanPath = when {
            checkpointUrl.startsWith("http") -> {
                val urlObj = checkpointUrl.toHttpUrlOrNull()
                urlObj?.encodedPath?.removePrefix("/") ?: "challenge/"
            }
            else -> checkpointUrl.removePrefix("/")
        }

        if (!securityCode.isNullOrBlank()) {
            val loginRes = submitChallengeSecurityCode(cleanPath, securityCode, username)
            return@withContext when (loginRes) {
                is LoginResult.Success -> ChallengeResult.Success(loginRes.session)
                is LoginResult.Failed -> ChallengeResult.Failed(loginRes.errorMessage, loginRes.errorType)
                is LoginResult.ChallengeNeeded -> ChallengeResult.StepRequired("challenge_needed", null)
                is LoginResult.TwoFactorNeeded -> ChallengeResult.StepRequired("two_factor_needed", null)
            }
        }

        if (!choice.isNullOrBlank()) {
            val res = selectChallengeMethod(cleanPath, choice)
            return@withContext if (res.isSuccess) {
                val resp = res.getOrThrow()
                if (resp.isCompleted && resp.loggedInUser != null) {
                    val session = createSessionFromCookies(resp.loggedInUser.pk, resp.loggedInUser.username, null)
                    sessionManager.saveSession(session)
                    ChallengeResult.Success(session)
                } else {
                    ChallengeResult.StepRequired(resp.stepName ?: "verify_code", resp.stepData)
                }
            } else {
                ChallengeResult.Failed(res.exceptionOrNull()?.message ?: "Challenge method selection failed")
            }
        }

        // Query status
        val stateRes = getChallengeState(cleanPath)
        if (stateRes.isSuccess) {
            val resp = stateRes.getOrThrow()
            ChallengeResult.StepRequired(resp.stepName ?: "unknown", resp.stepData)
        } else {
            ChallengeResult.Failed(stateRes.exceptionOrNull()?.message ?: "Challenge query failed")
        }
    }

    fun handleChallengeBlocking(
        checkpointUrl: String,
        choice: String? = null,
        securityCode: String? = null,
        username: String = ""
    ): ChallengeResult = runBlocking {
        handleChallenge(checkpointUrl, choice, securityCode, username)
    }

    // -------------------------------------------------------------
    // Feed, Stories, and Reels Operations
    // -------------------------------------------------------------

    suspend fun getTimelineFeed(
        maxId: String? = null,
        isPullToRefresh: Boolean = false
    ): Result<TimelineFeedResponse> = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply {
            add("phone_id", sessionManager.getOrGeneratePhoneId())
            add("battery_level", "100")
            add("timezone_offset", "12600") // UTC+3:30 (Tehran)
            add("_csrftoken", sessionManager.cookieJar.getCookieValue("csrftoken") ?: "")
            add("client_session_id", sessionManager.getOrGenerateUuid())
            add("device_id", sessionManager.getOrGenerateDeviceId())
            add("_uuid", sessionManager.getOrGenerateUuid())
            add("is_charging", "0")
            add("is_pull_to_refresh", if (isPullToRefresh) "1" else "0")
            add("reason", when {
                !maxId.isNullOrBlank() -> "pagination"
                isPullToRefresh -> "pull_to_refresh"
                else -> "cold_start_fetch"
            })
            if (!maxId.isNullOrBlank()) {
                add("max_id", maxId)
            }
        }.build()

        val request = buildRequest("api/v1/feed/timeline/")
            .post(form)
            .build()

        executeRequest(request, TimelineFeedResponse::class.java)
    }

    fun getTimelineFeedBlocking(maxId: String? = null): TimelineFeedResponse? = runBlocking {
        getTimelineFeed(maxId).getOrNull()
    }

    suspend fun getReelsTray(): Result<ReelsTrayResponse> = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("reason", "cold_start")
            .add("_uuid", sessionManager.getOrGenerateUuid())
            .add("_csrftoken", sessionManager.cookieJar.getCookieValue("csrftoken") ?: "")
            .build()

        val request = buildRequest("api/v1/feed/reels_tray/")
            .post(form)
            .build()

        executeRequest(request, ReelsTrayResponse::class.java)
    }

    fun getReelsTrayBlocking(): ReelsTrayResponse? = runBlocking {
        getReelsTray().getOrNull()
    }

    suspend fun getClipsDiscover(maxId: String? = null): Result<ClipsResponse> = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply {
            add("container_module", "clips_viewer_clips_tab")
            add("page_size", "12")
            add("include_feed_video", "true")
            if (!maxId.isNullOrBlank()) {
                add("max_id", maxId)
            }
        }.build()

        val request = buildRequest("api/v1/clips/discover/")
            .post(form)
            .build()

        executeRequest(request, ClipsResponse::class.java)
    }

    fun getClipsDiscoverBlocking(maxId: String? = null): ClipsResponse? = runBlocking {
        getClipsDiscover(maxId).getOrNull()
    }

    suspend fun getUserClips(userId: Long, maxId: String? = null): Result<UserClipsResponse> = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply {
            add("target_user_id", userId.toString())
            add("page_size", "12")
            if (!maxId.isNullOrBlank()) {
                add("max_id", maxId)
            }
        }.build()

        val request = buildRequest("api/v1/clips/user/")
            .post(form)
            .build()

        executeRequest(request, UserClipsResponse::class.java)
    }

    fun getUserClipsBlocking(userId: Long, maxId: String? = null): UserClipsResponse? = runBlocking {
        getUserClips(userId, maxId).getOrNull()
    }

    // -------------------------------------------------------------
    // Private Helpers & Internals
    // -------------------------------------------------------------

    private fun buildRequest(endpoint: String): Request.Builder {
        val cleanEndpoint = endpoint.removePrefix("/")
        return Request.Builder()
            .url("$BASE_URL$cleanEndpoint")
            .addHeader("User-Agent", InstagramDevice.DEFAULT_USER_AGENT)
            .addHeader("X-IG-App-ID", InstagramDevice.DEFAULT_APP_ID)
            .addHeader("X-IG-Device-ID", sessionManager.getOrGenerateUuid())
            .addHeader("X-IG-Android-ID", sessionManager.getOrGenerateDeviceId())
            .addHeader("X-IG-Connection-Type", "WIFI")
            .addHeader("X-IG-Capabilities", InstagramDevice.DEFAULT_CAPABILITIES)
            .addHeader("Accept-Language", "en-US")
    }

    private fun executeAuthRequest(request: Request, username: String): LoginResult {
        return try {
            val response = okHttpClient.newCall(request).execute()
            val rawBody = response.body?.string().orEmpty()
            val json = try {
                gson.fromJson(rawBody, JsonObject::class.java)
            } catch (e: Exception) {
                JsonObject()
            }

            if (response.isSuccessful && json.get("status")?.asString == "ok") {
                val userObj = json.getAsJsonObject("logged_in_user")
                val pk = userObj?.get("pk")?.asLong ?: 0L
                val uname = userObj?.get("username")?.asString ?: username
                val fullName = userObj?.get("full_name")?.asString
                val picUrl = userObj?.get("profile_pic_url")?.asString
                val bearerToken = response.header("ig-set-authorization")

                val session = InstagramSession(
                    userId = pk,
                    username = uname,
                    fullName = fullName,
                    profilePicUrl = picUrl,
                    sessionId = sessionManager.cookieJar.getCookieValue("sessionid").orEmpty(),
                    csrfToken = sessionManager.cookieJar.getCookieValue("csrftoken").orEmpty(),
                    dsUserId = sessionManager.cookieJar.getCookieValue("ds_user_id").orEmpty(),
                    mid = sessionManager.cookieJar.getCookieValue("mid"),
                    rur = sessionManager.cookieJar.getCookieValue("rur"),
                    bearerToken = bearerToken,
                    deviceId = sessionManager.getOrGenerateDeviceId(),
                    uuid = sessionManager.getOrGenerateUuid(),
                    phoneId = sessionManager.getOrGeneratePhoneId(),
                    adid = sessionManager.getOrGenerateAdid()
                )
                sessionManager.saveSession(session)
                LoginResult.Success(session)
            } else {
                // Check 2FA
                if (json.get("two_factor_required")?.asBoolean == true) {
                    val twoFactorInfo = gson.fromJson(json.get("two_factor_info"), TwoFactorInfo::class.java)
                    sessionManager.setAuthState(AuthState.TwoFactorRequired(twoFactorInfo, username))
                    LoginResult.TwoFactorNeeded(twoFactorInfo, username)
                }
                // Check Challenge Checkpoint
                else if (json.get("message")?.asString == "challenge_required" || json.has("challenge")) {
                    val challenge = gson.fromJson(json.get("challenge"), ChallengeInfo::class.java)
                        ?: ChallengeInfo(url = json.get("checkpoint_url")?.asString, apiPath = "challenge/")
                    sessionManager.setAuthState(AuthState.ChallengeRequired(challenge, username))
                    LoginResult.ChallengeNeeded(challenge, username)
                }
                // Check Bad Password
                else if (json.get("message")?.asString == "bad_password" || json.get("error_type")?.asString == "bad_password") {
                    LoginResult.Failed("نام کاربری یا رمز عبور نادرست است.", AuthErrorType.INVALID_CREDENTIALS)
                }
                // Rate limited / Feedback required
                else if (response.code == 429 || json.get("message")?.asString == "feedback_required") {
                    LoginResult.Failed("درخواست‌های مکرر. لطفاً دقایقی دیگر تلاش کنید.", AuthErrorType.RATE_LIMITED)
                }
                // Checkpoint blocked
                else if (json.get("message")?.asString == "checkpoint_required") {
                    LoginResult.Failed("حساب کاربری نیازمند بررسی امنیتی است.", AuthErrorType.CHECKPOINT_BLOCKED)
                } else {
                    val msg = json.get("message")?.asString ?: "خطا در برقراری ارتباط با اینستاگرام (کد: ${response.code})"
                    LoginResult.Failed(msg, AuthErrorType.UNKNOWN)
                }
            }
        } catch (e: IOException) {
            LoginResult.Failed("خطای برقراری ارتباط شبکه: ${e.localizedMessage}", AuthErrorType.NETWORK_FAILURE)
        }
    }

    private fun <T> executeRequest(request: Request, responseClass: Class<T>): Result<T> {
        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("HTTP Error ${response.code}: ${response.message}"))
            }
            val body = response.body?.string().orEmpty()
            val parsed = gson.fromJson(body, responseClass)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchPublicKeySync(): KeySyncResult {
        val request = Request.Builder()
            .url("${BASE_URL}api/v1/qe/sync/")
            .addHeader("User-Agent", InstagramDevice.DEFAULT_USER_AGENT)
            .addHeader("X-IG-App-ID", InstagramDevice.DEFAULT_APP_ID)
            .post("id=${sessionManager.getOrGenerateUuid()}".toRequestBody(jsonMediaType))
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            val keyId = response.header("ig-set-password-encryption-key-id") ?: "225"
            val pubKey = response.header("ig-set-password-encryption-pub-key") ?: DEFAULT_PUB_KEY
            KeySyncResult(keyId, pubKey)
        } catch (e: Exception) {
            KeySyncResult("225", DEFAULT_PUB_KEY)
        }
    }

    private fun createSessionFromCookies(userId: Long, username: String, fullName: String?): InstagramSession {
        return InstagramSession(
            userId = userId,
            username = username,
            fullName = fullName,
            sessionId = sessionManager.cookieJar.getCookieValue("sessionid").orEmpty(),
            csrfToken = sessionManager.cookieJar.getCookieValue("csrftoken").orEmpty(),
            dsUserId = sessionManager.cookieJar.getCookieValue("ds_user_id").orEmpty(),
            mid = sessionManager.cookieJar.getCookieValue("mid"),
            rur = sessionManager.cookieJar.getCookieValue("rur"),
            deviceId = sessionManager.getOrGenerateDeviceId(),
            uuid = sessionManager.getOrGenerateUuid(),
            phoneId = sessionManager.getOrGeneratePhoneId(),
            adid = sessionManager.getOrGenerateAdid()
        )
    }

    data class KeySyncResult(val keyId: String, val publicKey: String)

    companion object {
        const val BASE_URL = "https://i.instagram.com/"
        const val DEFAULT_PUB_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHl/5q4+Bw3k8PjJ0+m2P9f430sW90G8NvZk0z0Bv2gR" // Default 2048-bit fallback key

        fun createDefaultHttpClient(sessionManager: InstagramSessionManager): OkHttpClient {
            return org.fresh.instagram.network.ResilientHttpClientFactory.create(sessionManager)
        }
    }
}

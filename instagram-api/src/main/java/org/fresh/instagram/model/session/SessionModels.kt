package org.fresh.instagram.model.session

import com.google.gson.annotations.SerializedName
import org.fresh.instagram.model.feed.InstagramUser

/**
 * Persistent Instagram User Session and Credentials.
 * Encapsulates session cookies, bearer tokens, and device identification.
 */
data class InstagramSession(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("profile_pic_url") val profilePicUrl: String? = null,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("csrf_token") val csrfToken: String,
    @SerializedName("ds_user_id") val dsUserId: String,
    @SerializedName("mid") val mid: String? = null,
    @SerializedName("rur") val rur: String? = null,
    @SerializedName("bearer_token") val bearerToken: String? = null,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("uuid") val uuid: String,
    @SerializedName("phone_id") val phoneId: String,
    @SerializedName("adid") val adid: String? = null,
    @SerializedName("created_at_ms") val createdAtMs: Long = System.currentTimeMillis(),
    @SerializedName("is_authenticated") val isAuthenticated: Boolean = true
) {
    val isValid: Boolean
        get() = sessionId.isNotBlank() && userId > 0 && dsUserId.isNotBlank()
}

/**
 * Authentication state machine for UI reactive bindings.
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    data class Authenticated(val session: InstagramSession) : AuthState()
    data class TwoFactorRequired(val twoFactorInfo: TwoFactorInfo, val username: String) : AuthState()
    data class ChallengeRequired(val challenge: ChallengeInfo, val username: String) : AuthState()
    data class Error(val message: String, val errorType: AuthErrorType) : AuthState()
}

enum class AuthErrorType {
    INVALID_CREDENTIALS,
    RATE_LIMITED,
    CHECKPOINT_BLOCKED,
    NETWORK_FAILURE,
    UNKNOWN
}

/**
 * Two-Factor Authentication metadata returned on HTTP 400 Bad Request.
 */
data class TwoFactorInfo(
    @SerializedName("two_factor_identifier") val twoFactorIdentifier: String,
    @SerializedName("show_messenger_code_option") val showMessengerCodeOption: Boolean = false,
    @SerializedName("sms_two_factor_on") val smsTwoFactorOn: Boolean = false,
    @SerializedName("totp_two_factor_on") val totpTwoFactorOn: Boolean = false,
    @SerializedName("obfuscated_phone_number") val obfuscatedPhoneNumber: String? = null
) {
    companion object {
        const val METHOD_SMS = "1"
        const val METHOD_TOTP = "2"
    }
}

/**
 * Challenge checkpoint info returned from login failure.
 */
data class ChallengeInfo(
    @SerializedName("url") val url: String? = null,
    @SerializedName("api_path") val apiPath: String? = null,
    @SerializedName("checkpoint_url") val checkpointUrl: String? = null,
    @SerializedName("lock") val lock: Boolean = false,
    @SerializedName("logout") val logout: Boolean = false,
    @SerializedName("native_flow") val nativeFlow: Boolean = false,
    @SerializedName("challenge_context") val challengeContext: String? = null
)

/**
 * Challenge checkpoint state returned from /api/v1/challenge/{user_id}/{challenge_hash}/
 */
data class ChallengeResponse(
    @SerializedName("step_name") val stepName: String? = null, // select_verify_method, verify_code, delta_login_review
    @SerializedName("step_data") val stepData: ChallengeStepData? = null,
    @SerializedName("action") val action: String? = null, // "close" on completion
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("logged_in_user") val loggedInUser: InstagramUser? = null
) {
    val isCompleted: Boolean
        get() = "close".equals(action, ignoreCase = true) && "ok".equals(status, ignoreCase = true)
}

data class ChallengeStepData(
    @SerializedName("choice") val choice: String? = null, // "0" for SMS, "1" for Email
    @SerializedName("contact_point") val contactPoint: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("security_code") val securityCode: String? = null
)

/**
 * Results of login / 2FA / challenge actions.
 */
sealed class LoginResult {
    data class Success(val session: InstagramSession) : LoginResult()
    data class TwoFactorNeeded(val twoFactorInfo: TwoFactorInfo, val username: String) : LoginResult()
    data class ChallengeNeeded(val challenge: ChallengeInfo, val username: String) : LoginResult()
    data class Failed(val errorMessage: String, val errorType: AuthErrorType = AuthErrorType.UNKNOWN) : LoginResult()

    val isSuccessful: Boolean get() = this is Success
}

sealed class ChallengeResult {
    data class Success(val session: InstagramSession) : ChallengeResult()
    data class StepRequired(val stepName: String, val stepData: ChallengeStepData?) : ChallengeResult()
    data class Failed(val errorMessage: String, val errorType: AuthErrorType = AuthErrorType.UNKNOWN) : ChallengeResult()

    val isSuccessful: Boolean get() = this is Success
}

/**
 * Raw login API response deserialization helper.
 */
data class LoginResponse(
    @SerializedName("logged_in_user") val loggedInUser: InstagramUser? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_type") val errorType: String? = null,
    @SerializedName("two_factor_required") val twoFactorRequired: Boolean = false,
    @SerializedName("two_factor_info") val twoFactorInfo: TwoFactorInfo? = null,
    @SerializedName("checkpoint_url") val checkpointUrl: String? = null,
    @SerializedName("challenge") val challenge: ChallengeInfo? = null
) {
    val isSuccess: Boolean
        get() = "ok".equals(status, ignoreCase = true) && loggedInUser != null

    val isTwoFactor: Boolean
        get() = twoFactorRequired && twoFactorInfo != null

    val isChallenge: Boolean
        get() = "challenge_required".equals(message, ignoreCase = true) || checkpointUrl != null || challenge != null
}

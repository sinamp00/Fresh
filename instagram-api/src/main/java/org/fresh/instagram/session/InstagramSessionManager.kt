package org.fresh.instagram.session

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fresh.instagram.client.InstagramApiClient
import org.fresh.instagram.model.session.*
import java.util.UUID

/**
 * Central Instagram Session, Credential, and Auth State Coordinator.
 *
 * Implements full interface contracts for Telegram UI host integration:
 * - isLoggedIn()
 * - getCurrentSession()
 * - saveSession(session)
 * - loadSession()
 * - logout()
 * - login(username, password)
 * - verifyTwoFactor(twoFactorIdentifier, code, method)
 * - handleChallenge(checkpointUrl, choice, securityCode)
 */
class InstagramSessionManager(val context: Context) {

    val cookieJar: PersistentCookieJar = PersistentCookieJar(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var currentSession: InstagramSession? = null

    // Lazy initialization of InstagramApiClient
    val apiClient: InstagramApiClient by lazy {
        InstagramApiClient(this)
    }

    init {
        currentSession = loadSession()
        if (currentSession != null && isLoggedIn()) {
            _authState.value = AuthState.Authenticated(currentSession!!)
        }
    }

    /**
     * Determines whether there is an active authenticated session with valid sessionid cookie.
     */
    fun isLoggedIn(): Boolean {
        val session = currentSession ?: return false
        val sessionId = cookieJar.getCookieValue("sessionid")
        return session.isAuthenticated && !sessionId.isNullOrBlank()
    }

    fun getCurrentSession(): InstagramSession? = currentSession

    fun getActiveSession(): InstagramSession? = getCurrentSession()

    fun getOrGenerateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = "android-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun getOrGenerateUuid(): String {
        var uuid = prefs.getString(KEY_UUID, null)
        if (uuid.isNullOrBlank()) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_UUID, uuid).apply()
        }
        return uuid
    }

    fun getOrGeneratePhoneId(): String {
        var phoneId = prefs.getString(KEY_PHONE_ID, null)
        if (phoneId.isNullOrBlank()) {
            phoneId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_PHONE_ID, phoneId).apply()
        }
        return phoneId
    }

    fun getOrGenerateAdid(): String {
        var adid = prefs.getString(KEY_ADID, null)
        if (adid.isNullOrBlank()) {
            adid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_ADID, adid).apply()
        }
        return adid
    }

    @Synchronized
    fun saveSession(session: InstagramSession) {
        currentSession = session
        val json = gson.toJson(session)
        prefs.edit()
            .putString(KEY_SESSION_DATA, json)
            .putString(KEY_DEVICE_ID, session.deviceId)
            .putString(KEY_UUID, session.uuid)
            .putString(KEY_PHONE_ID, session.phoneId)
            .putString(KEY_ADID, session.adid)
            .apply()
        _authState.value = AuthState.Authenticated(session)
    }

    @Synchronized
    fun loadSession(): InstagramSession? {
        val json = prefs.getString(KEY_SESSION_DATA, null) ?: return null
        return try {
            gson.fromJson(json, InstagramSession::class.java)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun logout() {
        currentSession = null
        cookieJar.clear()
        prefs.edit().remove(KEY_SESSION_DATA).apply()
        _authState.value = AuthState.Unauthenticated
    }

    fun clearSession() = logout()

    fun setAuthState(state: AuthState) {
        _authState.value = state
    }

    // -------------------------------------------------------------
    // Direct Auth Delegates (Interface Contract Compliance)
    // -------------------------------------------------------------

    suspend fun login(username: String, password: String): LoginResult {
        _authState.value = AuthState.Authenticating
        val result = apiClient.login(username, password)
        when (result) {
            is LoginResult.Success -> {
                saveSession(result.session)
            }
            is LoginResult.TwoFactorNeeded -> {
                _authState.value = AuthState.TwoFactorRequired(result.twoFactorInfo, username)
            }
            is LoginResult.ChallengeNeeded -> {
                _authState.value = AuthState.ChallengeRequired(result.challenge, username)
            }
            is LoginResult.Failed -> {
                _authState.value = AuthState.Error(result.errorMessage, result.errorType)
            }
        }
        return result
    }

    fun loginBlocking(username: String, password: String): LoginResult =
        apiClient.loginBlocking(username, password)

    suspend fun verifyTwoFactor(twoFactorIdentifier: String, code: String, method: Int = 1): LoginResult {
        val username = when (val state = _authState.value) {
            is AuthState.TwoFactorRequired -> state.username
            else -> currentSession?.username.orEmpty()
        }
        val result = apiClient.verifyTwoFactor(
            username = username,
            twoFactorIdentifier = twoFactorIdentifier,
            code = code,
            verificationMethod = method.toString()
        )
        if (result is LoginResult.Success) {
            saveSession(result.session)
        }
        return result
    }

    fun verifyTwoFactorBlocking(twoFactorIdentifier: String, code: String, method: Int = 1): LoginResult =
        apiClient.verifyTwoFactorBlocking(
            username = currentSession?.username.orEmpty(),
            twoFactorIdentifier = twoFactorIdentifier,
            code = code,
            verificationMethod = method.toString()
        )

    suspend fun handleChallenge(checkpointUrl: String, choice: String? = null, securityCode: String? = null): ChallengeResult {
        val username = when (val state = _authState.value) {
            is AuthState.ChallengeRequired -> state.username
            else -> currentSession?.username.orEmpty()
        }
        val result = apiClient.handleChallenge(checkpointUrl, choice, securityCode, username)
        if (result is ChallengeResult.Success) {
            saveSession(result.session)
        }
        return result
    }

    fun handleChallengeBlocking(checkpointUrl: String, choice: String? = null, securityCode: String? = null): ChallengeResult =
        apiClient.handleChallengeBlocking(
            checkpointUrl = checkpointUrl,
            choice = choice,
            securityCode = securityCode,
            username = currentSession?.username.orEmpty()
        )

    companion object {
        private const val PREF_SESSION = "fresh_instagram_session_vault"
        private const val KEY_SESSION_DATA = "session_json"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_UUID = "uuid"
        private const val KEY_PHONE_ID = "phone_id"
        private const val KEY_ADID = "adid"

        @Volatile
        private var INSTANCE: InstagramSessionManager? = null

        @JvmStatic
        fun getInstance(context: Context): InstagramSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InstagramSessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

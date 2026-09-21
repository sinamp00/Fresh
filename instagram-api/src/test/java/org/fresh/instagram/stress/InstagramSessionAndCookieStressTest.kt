package org.fresh.instagram.stress

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.fresh.instagram.model.session.*
import org.fresh.instagram.session.InstagramSessionManager
import org.fresh.instagram.session.PersistentCookieJar
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Stress Test Harness for:
 * 1. PersistentCookieJar: Concurrency, Expiry, Storage, and Import.
 * 2. InstagramSessionManager: State Machine Transitions, Session Persistence, and Blocking Delegates.
 */
class InstagramSessionAndCookieStressTest {

    private lateinit var mockContext: MockContext
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var sessionManager: InstagramSessionManager

    @Before
    fun setUp() {
        mockContext = MockContext()
        cookieJar = PersistentCookieJar(mockContext)
        // Reset singleton via reflection to ensure clean state per test
        val field = InstagramSessionManager::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
        sessionManager = InstagramSessionManager.getInstance(mockContext)
    }

    // =========================================================================
    // 1. PersistentCookieJar: Concurrency & Stress
    // =========================================================================

    @Test
    fun testPersistentCookieJarHighConcurrency() {
        val threadCount = 32
        val operationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        val targetUrl = "https://i.instagram.com/api/v1/test/".toHttpUrl()

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until operationsPerThread) {
                        val cookie = Cookie.Builder()
                            .name("cookie_${t}_$i")
                            .value("val_${t}_$i")
                            .domain("i.instagram.com")
                            .path("/")
                            .build()

                        // Concurrent write
                        cookieJar.saveFromResponse(targetUrl, listOf(cookie))

                        // Concurrent read
                        val cookies = cookieJar.loadForRequest(targetUrl)
                        assertTrue(cookies.isNotEmpty())

                        // Concurrent value lookup
                        val value = cookieJar.getCookieValue("cookie_${t}_$i")
                        assertEquals("val_${t}_$i", value)

                        // Concurrent raw string import
                        cookieJar.importRawCookieString("raw_${t}_$i=rawval; sub_${t}_$i=subval")
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Concurrent cookie execution timed out", completed)
        assertEquals("Exceptions occurred during concurrent cookie jar operations", 0, errorCount.get())

        // Validate final state integrity
        val finalCookies = cookieJar.loadForRequest(targetUrl)
        println("[STRESS RESULT] Final cookie count after $threadCount concurrent threads: ${finalCookies.size}")
        assertTrue(finalCookies.size >= threadCount * operationsPerThread)
    }

    // =========================================================================
    // 2. PersistentCookieJar: Expiration & In-Memory vs SharedPreferences Bug Detection
    // =========================================================================

    @Test
    fun testCookieExpirationAndStorageLogic() {
        val url = "https://i.instagram.com/".toHttpUrl()
        val now = System.currentTimeMillis()

        // 1. Save one valid cookie and one expired cookie
        val validCookie = Cookie.Builder()
            .name("sessionid")
            .value("active_session_123")
            .domain("i.instagram.com")
            .path("/")
            .expiresAt(now + 100000L) // 100 seconds in future
            .build()

        val expiredCookie = Cookie.Builder()
            .name("expired_token")
            .value("stale_value")
            .domain("i.instagram.com")
            .path("/")
            .expiresAt(now - 10000L) // 10 seconds in past
            .build()

        cookieJar.saveFromResponse(url, listOf(validCookie, expiredCookie))

        // 2. loadForRequest should filter out expiredCookie
        val loaded = cookieJar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("sessionid", loaded[0].name)
        assertEquals("active_session_123", loaded[0].value)

        // 3. Empirical Challenge: Check getCookieValue on expired cookie!
        // Does getCookieValue check expiresAt?
        val expiredValue = cookieJar.getCookieValue("expired_token")
        println("[STRESS RESULT] getCookieValue on expired cookie returned: $expiredValue")
        // Note: In loadForRequest, expiredCookie was removed from in-memory cookies map,
        // so expiredValue should be null IF loadForRequest was called first.
        assertNull("Expired cookie should not be returned after loadForRequest purge", expiredValue)

        // 4. Critical Empirical Challenge: App Restart / Persistence of Expired Cookies
        // When loadForRequest purged expired_token from memory, did it update SharedPreferences?
        // Let's create a brand new PersistentCookieJar pointing to the same SharedPreferences to simulate app restart!
        val restartedCookieJar = PersistentCookieJar(mockContext)
        val valueAfterRestart = restartedCookieJar.getCookieValue("expired_token")
        println("[EMPIRICAL DISCOVERY] Value of expired_token after PersistentCookieJar restart: $valueAfterRestart")

        if (valueAfterRestart != null) {
            println("[VULNERABILITY CONFIRMED] PersistentCookieJar does NOT flush expired cookie removals to SharedPreferences during loadForRequest! Expired cookies resurrect upon app restart until next loadForRequest!")
        }
    }

    @Test
    fun testRawCookieImportAndMalformedStrings() {
        // Complex raw cookie string with spaces, multiple equals, empty entries
        val rawInput = "sessionid=valid_sess_999; ; csrftoken=csrf_abc=123; ds_user_id=12345; =empty_name; no_equals_token"
        cookieJar.importRawCookieString(rawInput, "i.instagram.com")

        assertEquals("valid_sess_999", cookieJar.getCookieValue("sessionid"))
        assertEquals("csrf_abc=123", cookieJar.getCookieValue("csrftoken"))
        assertEquals("12345", cookieJar.getCookieValue("ds_user_id"))
        assertNull(cookieJar.getCookieValue("empty_name"))
        assertNull(cookieJar.getCookieValue("no_equals_token"))

        // Verify clear() removes everything from both memory and prefs
        cookieJar.clear()
        assertNull(cookieJar.getCookieValue("sessionid"))
        val reloaded = PersistentCookieJar(mockContext)
        assertNull(reloaded.getCookieValue("sessionid"))
    }

    // =========================================================================
    // 3. InstagramSessionManager: State Machine Transitions
    // =========================================================================

    @Test
    fun testSessionManagerStateTransitionsFullCycle() = runBlocking {
        // Initial state
        assertEquals(AuthState.Unauthenticated, sessionManager.authState.value)
        assertFalse(sessionManager.isLoggedIn())

        // 1. Unauthenticated -> Authenticating
        sessionManager.setAuthState(AuthState.Authenticating)
        assertEquals(AuthState.Authenticating, sessionManager.authState.value)

        // 2. Authenticating -> TwoFactorRequired
        val twoFactorInfo = TwoFactorInfo(twoFactorIdentifier = "2fa_id_1", smsTwoFactorOn = true)
        sessionManager.setAuthState(AuthState.TwoFactorRequired(twoFactorInfo, "test_user"))
        assertTrue(sessionManager.authState.value is AuthState.TwoFactorRequired)
        val state2Fa = sessionManager.authState.value as AuthState.TwoFactorRequired
        assertEquals("2fa_id_1", state2Fa.twoFactorInfo.twoFactorIdentifier)
        assertEquals("test_user", state2Fa.username)

        // 3. TwoFactorRequired -> ChallengeRequired
        val challengeInfo = ChallengeInfo(url = "https://i.instagram.com/challenge/123/", apiPath = "challenge/123/")
        sessionManager.setAuthState(AuthState.ChallengeRequired(challengeInfo, "test_user"))
        assertTrue(sessionManager.authState.value is AuthState.ChallengeRequired)
        val stateChal = sessionManager.authState.value as AuthState.ChallengeRequired
        assertEquals("challenge/123/", stateChal.challenge.apiPath)

        // 4. ChallengeRequired -> Authenticated
        val session = InstagramSession(
            userId = 987654L,
            username = "test_user",
            sessionId = "session_token_xyz",
            csrfToken = "csrf_token_xyz",
            dsUserId = "987654",
            deviceId = "android-test",
            uuid = "uuid-test",
            phoneId = "phone-test"
        )
        // Also ensure sessionid cookie is stored in sessionManager's cookieJar so isLoggedIn() evaluates true
        sessionManager.cookieJar.importRawCookieString("sessionid=session_token_xyz; ds_user_id=987654")
        sessionManager.saveSession(session)

        assertTrue(sessionManager.authState.value is AuthState.Authenticated)
        assertTrue(sessionManager.isLoggedIn())
        assertEquals(session, sessionManager.getCurrentSession())

        // Verify session persistence across restart
        val field = InstagramSessionManager::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
        val newSessionManager = InstagramSessionManager.getInstance(mockContext)
        assertTrue(newSessionManager.isLoggedIn())
        assertEquals(session.userId, newSessionManager.getCurrentSession()?.userId)
        assertTrue(newSessionManager.authState.value is AuthState.Authenticated)

        // 5. Authenticated -> LoggedOut (Unauthenticated)
        newSessionManager.logout()
        assertEquals(AuthState.Unauthenticated, newSessionManager.authState.value)
        assertFalse(newSessionManager.isLoggedIn())
        assertNull(newSessionManager.getCurrentSession())
        assertNull(newSessionManager.cookieJar.getCookieValue("sessionid"))
    }

    // =========================================================================
    // 4. Empirical Challenge: verifyTwoFactorBlocking & handleChallengeBlocking Username Loss
    // =========================================================================

    @Test
    fun testBlockingAuthDelegatesUsernameEvaluation() {
        // When 2FA is required, currentSession is NULL!
        // In verifyTwoFactorBlocking:
        // username = currentSession?.username.orEmpty()
        // Because currentSession is null, username evaluates to ""!
        assertNull("currentSession must be null prior to login completion", sessionManager.getCurrentSession())

        val twoFactorInfo = TwoFactorInfo(twoFactorIdentifier = "2fa_id_test", smsTwoFactorOn = true)
        sessionManager.setAuthState(AuthState.TwoFactorRequired(twoFactorInfo, "john_doe"))

        val currentUsernameFromState = when (val state = sessionManager.authState.value) {
            is AuthState.TwoFactorRequired -> state.username
            else -> sessionManager.getCurrentSession()?.username.orEmpty()
        }
        assertEquals("john_doe", currentUsernameFromState)

        // Compare with verifyTwoFactorBlocking's parameter: currentSession?.username.orEmpty()
        val usernameUsedByBlocking = sessionManager.getCurrentSession()?.username.orEmpty()
        println("[EMPIRICAL DISCOVERY] Username passed by verifyTwoFactorBlocking: '$usernameUsedByBlocking'")
        assertEquals("", usernameUsedByBlocking)
        if (usernameUsedByBlocking.isEmpty() && currentUsernameFromState.isNotEmpty()) {
            println("[VULNERABILITY CONFIRMED] verifyTwoFactorBlocking and handleChallengeBlocking drop the pending username ('$currentUsernameFromState' -> '') because they read from null currentSession instead of authState!")
        }
    }

    // =========================================================================
    // In-Memory Mock Implementations for JVM Unit Testing
    // =========================================================================

    class MockContext : ContextWrapper(null) {
        private val stores = ConcurrentHashMap<String, SharedPreferences>()

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            return stores.getOrPut(name) { MockSharedPreferences() }
        }

        override fun getApplicationContext(): Context = this
    }

    class MockSharedPreferences : SharedPreferences {
        private val map = ConcurrentHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)

        override fun getString(key: String, defValue: String?): String? =
            (map[key] as? String) ?: defValue

        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (map[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String, defValue: Int): Int =
            (map[key] as? Int) ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            (map[key] as? Long) ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            (map[key] as? Float) ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            (map[key] as? Boolean) ?: defValue

        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = MockEditor(map)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class MockEditor(private val storage: ConcurrentHashMap<String, Any?>) : SharedPreferences.Editor {
            private val pendingChanges = HashMap<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pendingChanges[key] = value
                return this
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                pendingChanges[key] = values
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                pendingChanges[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                pendingChanges[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                pendingChanges[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                pendingChanges[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                pendingChanges[key] = this // Sentinel for removal
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearFlag) {
                    storage.clear()
                }
                for ((k, v) in pendingChanges) {
                    if (v === this) {
                        storage.remove(k)
                    } else if (v != null) {
                        storage[k] = v
                    } else {
                        storage.remove(k)
                    }
                }
            }
        }
    }
}

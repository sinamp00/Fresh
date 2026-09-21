package org.fresh.instagram.session

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe Persistent CookieJar for OkHttp.
 * Synchronizes cookies with Android SharedPreferences.
 * Supports importing raw cookie strings (for WebView checkpoint bridge).
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val cookieStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    init {
        loadPersistedCookies()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val hostCookies = cookieStore.getOrPut(host) { ConcurrentHashMap() }
        var changed = false

        for (cookie in cookies) {
            hostCookies[cookie.name] = cookie
            changed = true
        }

        if (changed) {
            persistHostCookies(host, hostCookies.values)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val validCookies = mutableListOf<Cookie>()
        val currentTime = System.currentTimeMillis()

        // Match domain and parent domains (e.g. .instagram.com matches i.instagram.com)
        cookieStore.forEach { (storedHost, cookies) ->
            if (host == storedHost || host.endsWith(".$storedHost") || storedHost.endsWith(".$host")) {
                val expiredNames = mutableListOf<String>()
                for (cookie in cookies.values) {
                    if (cookie.expiresAt < currentTime) {
                        expiredNames.add(cookie.name)
                    } else if (cookie.matches(url)) {
                        validCookies.add(cookie)
                    }
                }
                expiredNames.forEach { cookies.remove(it) }
            }
        }

        return validCookies
    }

    /**
     * Retrieve a specific cookie by name (e.g., "sessionid", "csrftoken", "ds_user_id").
     */
    fun getCookieValue(name: String, domain: String = "instagram.com"): String? {
        cookieStore.forEach { (host, cookies) ->
            if (host.contains(domain)) {
                cookies[name]?.let { return it.value }
            }
        }
        // Fallback: search all hosts
        cookieStore.values.forEach { map ->
            map[name]?.let { return it.value }
        }
        return null
    }

    /**
     * Import raw cookie string (e.g. from android.webkit.CookieManager).
     * Format: "sessionid=xyz; csrftoken=abc; ds_user_id=123"
     */
    @Synchronized
    fun importRawCookieString(cookieString: String, domain: String = "i.instagram.com") {
        val pairs = cookieString.split(";")
        val cookies = mutableListOf<Cookie>()
        for (pair in pairs) {
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name.isNotEmpty()) {
                    val cookie = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(domain.removePrefix("."))
                        .path("/")
                        .secure()
                        .httpOnly()
                        .build()
                    cookies.add(cookie)
                }
            }
        }
        val cleanHost = domain.removePrefix(".")
        val httpUrl = HttpUrl.Builder().scheme("https").host(cleanHost).build()
        saveFromResponse(httpUrl, cookies)
    }

    @Synchronized
    fun clear() {
        cookieStore.clear()
        prefs.edit().clear().apply()
    }

    private fun persistHostCookies(host: String, cookies: Collection<Cookie>) {
        val serializableList = cookies.map { SerializableCookieWrapper.from(it) }
        val json = gson.toJson(serializableList)
        prefs.edit().putString(KEY_HOST_PREFIX + host, json).apply()
    }

    private fun loadPersistedCookies() {
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_HOST_PREFIX) && value is String) {
                val host = key.removePrefix(KEY_HOST_PREFIX)
                val type = object : TypeToken<List<SerializableCookieWrapper>>() {}.type
                try {
                    val list: List<SerializableCookieWrapper> = gson.fromJson(value, type)
                    val map = ConcurrentHashMap<String, Cookie>()
                    for (wrapper in list) {
                        val cookie = wrapper.toCookie()
                        map[cookie.name] = cookie
                    }
                    cookieStore[host] = map
                } catch (ignored: Exception) {}
            }
        }
    }

    private data class SerializableCookieWrapper(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    ) {
        fun toCookie(): Cookie {
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(path)
            if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            return builder.build()
        }

        companion object {
            fun from(cookie: Cookie): SerializableCookieWrapper =
                SerializableCookieWrapper(
                    name = cookie.name,
                    value = cookie.value,
                    expiresAt = cookie.expiresAt,
                    domain = cookie.domain,
                    path = cookie.path,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly
                )
        }
    }

    companion object {
        private const val PREF_NAME = "fresh_instagram_cookies"
        private const val KEY_HOST_PREFIX = "host_"
    }
}

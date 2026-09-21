package org.fresh.instagram.network

import okhttp3.OkHttpClient
import org.fresh.instagram.session.InstagramSessionManager
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Builds OkHttpClient configured with DPI-bypass socket fragmentation, Clean DNS, and proxy bridging.
 */
object ResilientHttpClientFactory {

    fun create(
        sessionManager: InstagramSessionManager,
        enableDpiBypass: Boolean = TelegramProxyBridge.enableDpiBypass,
        proxyConfig: FreshProxyConfig? = null
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(sessionManager.cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .dns(CleanDnsProvider())

        if (enableDpiBypass) {
            builder.socketFactory(FragmentingSocketFactory(splitChunkSize = 24, delayMs = 6L))
        }

        val effectiveProxy = proxyConfig ?: TelegramProxyBridge.activeConfig
        if (effectiveProxy.enabled && effectiveProxy.toJavaProxy() != Proxy.NO_PROXY) {
            builder.proxy(effectiveProxy.toJavaProxy())
        }

        return builder.build()
    }
}

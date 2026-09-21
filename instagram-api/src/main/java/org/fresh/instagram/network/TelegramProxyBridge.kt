package org.fresh.instagram.network

import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy

/**
 * Proxy configuration model for Fresh Instagram integration.
 */
data class FreshProxyConfig(
    val enabled: Boolean = false,
    val type: Proxy.Type = Proxy.Type.DIRECT,
    val host: String = "",
    val port: Int = 0,
    val user: String? = null,
    val pass: String? = null
) {
    fun toJavaProxy(): Proxy {
        if (!enabled || host.isBlank() || port <= 0 || type == Proxy.Type.DIRECT) {
            return Proxy.NO_PROXY
        }
        return Proxy(type, InetSocketAddress(host, port))
    }
}

/**
 * Bridge allowing Instagram API requests to route seamlessly through Telegram's configured proxy.
 */
object TelegramProxyBridge {
    @Volatile
    @JvmField
    var activeConfig: FreshProxyConfig = FreshProxyConfig()

    @Volatile
    @JvmField
    var enableDpiBypass: Boolean = true

    @JvmStatic
    fun updateProxy(config: FreshProxyConfig) {
        activeConfig = config
        if (config.enabled && !config.user.isNullOrEmpty() && !config.pass.isNullOrEmpty()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    if (requestingHost.equals(config.host, ignoreCase = true) && requestingPort == config.port) {
                        return PasswordAuthentication(config.user, config.pass.toCharArray())
                    }
                    return super.getPasswordAuthentication()
                }
            })
        }
    }
}

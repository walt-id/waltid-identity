package id.walt.webdatafetching.config

import io.ktor.client.engine.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class RequestEngineConfiguration(
    val followRedirects: Boolean = true,
    val pipelining: Boolean = false,
    val proxy: ProxyConfiguration? = null,
) {
    @Serializable
    sealed class ProxyConfiguration {
        abstract fun toKtorProxyConfig(): ProxyConfig
    }

    @Serializable
    data class HttpProxyConfiguration(val url: Url) : ProxyConfiguration() {
        override fun toKtorProxyConfig() = ProxyBuilder.http(url)
    }

    @Serializable
    data class SocksProxyConfiguration(val host: String, val port: Int) : ProxyConfiguration() {
        override fun toKtorProxyConfig() = ProxyBuilder.socks(host, port)
    }
}

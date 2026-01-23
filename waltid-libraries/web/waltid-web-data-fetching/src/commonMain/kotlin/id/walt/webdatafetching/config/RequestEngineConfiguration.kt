package id.walt.webdatafetching.config

import io.ktor.client.engine.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
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
    @SerialName("http")
    data class HttpProxyConfiguration(val url: Url) : ProxyConfiguration() {
        override fun toKtorProxyConfig() = ProxyBuilder.http(url)
    }

    @Serializable
    @SerialName("socks")
    data class SocksProxyConfiguration(val host: String, val port: Int) : ProxyConfiguration() {
        override fun toKtorProxyConfig() = ProxyBuilder.socks(host, port)
    }

    companion object {
        val Example = RequestEngineConfiguration(
            followRedirects = false,
            pipelining = false,
            proxy = SocksProxyConfiguration("127.0.0.1", 12345)
        )
    }
}

package id.walt.webdatafetching.config.url

import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.utils.UrlUtils
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class UrlConfiguration(
    val protocols: UrlProtocols? = null,
    val ports: AllowList<Int>? = null,
    val hosts: AllowList<String>? = null,
    val urls: AllowList<Url>? = null
) {

    fun requireUrlAllowed(url: String) {
        val parsedUrl = UrlUtils.parseUrl(url)

        if (protocols != null) {
            require(protocols.isUrlAllowed(parsedUrl)) { "URL Protocol is not allowed: ${parsedUrl.protocol}" }
        }

        if (ports != null) {
            require(ports.isAllowed(parsedUrl.port)) { "Port is not allowed: ${parsedUrl.port}" }
        }

        if (hosts != null) {
            require(hosts.isAllowed(parsedUrl.host)) { "Host is not allowed: ${parsedUrl.host}" }
        }

        if (urls != null) {
            require(urls.isAllowed(parsedUrl)) { "URL is not allowed: $parsedUrl" }
        }
    }

    companion object {
        val Example = UrlConfiguration(
            protocols = UrlProtocols.Default,
            ports = AllowList(whitelist = listOf(80, 443), blacklist = listOf(25)),
            hosts = AllowList(whitelist = listOf("localhost", "127.0.0.1"), blacklist = listOf("google.com")),
            urls = AllowList(whitelist = listOf(Url("https://example.org/allowed")), blacklist = listOf(Url("https://example.org/disallowed")),)
        )
    }

}

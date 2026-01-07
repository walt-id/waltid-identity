package id.walt.webdatafetching.config.url

import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.utils.UrlUtils
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class UrlConfiguration(
    val protocols: UrlProtocols?,
    val ports: AllowList<Int>?,
    val hosts: AllowList<String>?,
    val urls: AllowList<Url>?,
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

}

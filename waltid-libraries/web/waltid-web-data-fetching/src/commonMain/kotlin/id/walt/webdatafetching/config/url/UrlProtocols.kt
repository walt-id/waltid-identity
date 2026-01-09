package id.walt.webdatafetching.config.url

import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class UrlProtocols(
    val allowHttp: Boolean = true,
    val allowHttps: Boolean = true
) {
    fun isUrlAllowed(url: Url) = when {
        allowHttp && url.protocol == URLProtocol.HTTP -> true
        allowHttps && url.protocol == URLProtocol.HTTPS -> true
        else -> false
    }

    companion object {
        val Default: UrlProtocols = UrlProtocols(
            allowHttp = true,
            allowHttps = true
        )
    }
}

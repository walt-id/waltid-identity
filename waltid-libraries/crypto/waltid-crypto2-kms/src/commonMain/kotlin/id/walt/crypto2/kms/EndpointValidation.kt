package id.walt.crypto2.kms

import io.ktor.http.URLProtocol
import io.ktor.http.Url

internal fun requireHttpEndpoint(value: String, name: String) {
    val authority = value.substringAfter("://", "").takeWhile { it != '/' && it != '?' && it != '#' }
    require(authority.isNotBlank()) { "$name must include a host" }
    val url = runCatching { Url(value) }
        .getOrElse { throw IllegalArgumentException("$name must be a valid HTTP or HTTPS URL", it) }
    require(url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS) {
        "$name must use HTTP or HTTPS"
    }
    require(url.host.isNotBlank()) { "$name must include a host" }
    require(url.user == null && url.password == null) { "$name must not contain user information" }
    require(url.parameters.isEmpty()) { "$name must not contain a query" }
    require(url.fragment.isEmpty()) { "$name must not contain a fragment" }
}

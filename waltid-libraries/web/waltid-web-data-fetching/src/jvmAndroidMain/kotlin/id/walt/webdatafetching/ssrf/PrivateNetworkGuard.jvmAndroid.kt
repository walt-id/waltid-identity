package id.walt.webdatafetching.ssrf

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

private val log = KotlinLogging.logger("PrivateNetworkGuard")

private const val MAX_REDIRECTS = 5
private val ALLOWED_SCHEMES = setOf("http", "https")
private val REDIRECT_STATUSES = setOf(
    HttpStatusCode.MovedPermanently, HttpStatusCode.Found, HttpStatusCode.SeeOther,
    HttpStatusCode.TemporaryRedirect, HttpStatusCode.PermanentRedirect,
)
private val DOWNGRADE_TO_GET_STATUSES = setOf(
    HttpStatusCode.MovedPermanently, HttpStatusCode.Found, HttpStatusCode.SeeOther,
)

/** True if [host] equals, or is a subdomain of, one of [PrivateNetworkGuardSettings.trustedHostSuffixes]. */
private fun isTrustedHost(host: String): Boolean =
    PrivateNetworkGuardSettings.trustedHostSuffixes.any { suffix ->
        host.equals(suffix, ignoreCase = true) || host.endsWith(".$suffix", ignoreCase = true)
    }

private fun isBlockedAddress(address: InetAddress, trustedHost: Boolean): Boolean {
    // Never legitimate for a server-side fetch, regardless of host trust - no opt-out.
    if (address.isLinkLocalAddress) return true // covers 169.254.0.0/16, including the cloud-metadata address
    if (address.isMulticastAddress) return true
    if (address.isAnyLocalAddress) return true

    // A trusted host skips only the loopback/private-network checks below - see the doc comment on
    // PrivateNetworkGuardSettings.trustedHostSuffixes for why this is narrower than the allow* flags.
    if (trustedHost) return false

    if (!PrivateNetworkGuardSettings.allowLoopback && address.isLoopbackAddress) return true

    if (!PrivateNetworkGuardSettings.allowPrivateNetworks) {
        if (address.isSiteLocalAddress) return true // RFC1918 IPv4
        val bytes = address.address
        if (bytes.size == 16) {
            // IPv6 unique local (fc00::/7): isSiteLocalAddress() only recognizes the deprecated fec0::/10 range.
            val first = bytes[0].toInt() and 0xff
            if (first == 0xfc || first == 0xfd) return true
        }
    }
    return false
}

private suspend fun requireAllowedTarget(url: Url) {
    require(url.protocol.name in ALLOWED_SCHEMES) {
        "Refusing to fetch '${url.protocol.name}://...': only http/https are allowed"
    }
    val host = url.host
    val addresses = try {
        withContext(Dispatchers.IO) { InetAddress.getAllByName(host) }
    } catch (e: UnknownHostException) {
        throw BlockedAddressException("Could not resolve host '$host'")
    }
    val trustedHost = isTrustedHost(host)
    val blocked = addresses.firstOrNull { isBlockedAddress(it, trustedHost) }
    if (blocked != null) {
        log.warn { "Refusing to fetch $url: '$host' resolves to ${blocked.hostAddress}, which is a blocked address" }
        throw BlockedAddressException("Refusing to fetch '$host': resolves to a blocked (private/loopback/link-local) address")
    }
}

/**
 * Resolves a Location header against the request it came from. Only absolute URLs are supported - real-world
 * OAuth/issuer redirects always send one. A relative Location is refused as unsupported, not as a blocked
 * address - it's a protocol edge case this guard doesn't implement RFC 3986 resolution for, not a security hit.
 */
internal class UnsupportedRedirectException(message: String) : Exception(message)

private val ABSOLUTE_URL_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

private fun resolveRedirectTarget(base: Url, location: String): Url {
    // Url(String) defaults a missing host to "localhost" rather than leaving it blank, so a relative Location
    // like "/relative-path" would otherwise be misparsed as pointing at localhost. Require an explicit scheme.
    if (ABSOLUTE_URL_SCHEME.containsMatchIn(location)) {
        runCatching { Url(location) }.getOrNull()?.let { return it }
    }
    throw UnsupportedRedirectException("Refusing to follow relative redirect Location '$location' from $base")
}

actual fun HttpClientConfig<*>.installPrivateNetworkGuard() {
    install(createClientPlugin("PrivateNetworkGuard") {
        on(Send) { request ->
            requireAllowedTarget(request.url.build())
            var call = proceed(request)
            var currentRequest = request
            var hops = 0
            while (call.response.status in REDIRECT_STATUSES) {
                val location = call.response.headers[HttpHeaders.Location] ?: break
                check(hops < MAX_REDIRECTS) { "Refusing to follow more than $MAX_REDIRECTS redirects" }
                hops++

                val target = resolveRedirectTarget(currentRequest.url.build(), location)
                requireAllowedTarget(target)

                val nextRequest = HttpRequestBuilder().takeFrom(currentRequest).apply {
                    url(target)
                    if (call.response.status in DOWNGRADE_TO_GET_STATUSES) {
                        method = HttpMethod.Get
                        headers.remove(HttpHeaders.ContentLength)
                        headers.remove(HttpHeaders.ContentType)
                    }
                }
                currentRequest = nextRequest
                call = proceed(currentRequest)
            }
            call
        }
    })
}

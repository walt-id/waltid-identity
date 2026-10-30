package id.walt.oid4vc.util

import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.ssrf.installPrivateNetworkGuard
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

val http = oid4vcHttpClient {

    val log = KotlinLogging.logger("id.walt.oid4vc.http")

    engine {
        log.info { "Initializing oid4vc HTTP client with engine config: ${this::class.simpleName}" }
    }

    // Response cap, timeouts, no redirects or retries, redacted header logs, and JSON content negotiation.
    WebDataFetchingConfiguration().applyConfigurationToHttpClient(this)

    // Fetches caller-supplied issuer/request URLs server-side (resolveCIProviderMetadata, etc.) - a potential
    // SSRF target, so guard it the same way WebDataFetcher-backed clients are guarded.
    // JVM uses OkHttp so that guard's DNS is the one the connection uses.
    installPrivateNetworkGuard()
}

/** JVM uses OkHttp so [installPrivateNetworkGuard] can pin the connection to the addresses it checked. */
internal expect fun oid4vcHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

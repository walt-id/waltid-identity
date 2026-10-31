package id.walt.oid4vc.util

import id.walt.webdatafetching.ssrf.installPrivateNetworkGuard
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

val http = oid4vcHttpClient {

    val log = KotlinLogging.logger("id.walt.oid4vc.http")

    engine {
        log.info { "Initializing oid4vc HTTP client with engine config: ${this::class.simpleName}" }
    }

    // Fetches caller-supplied issuer/request URLs server-side (resolveCIProviderMetadata, etc.) - a potential
    // SSRF target, so guard it the same way WebDataFetcher-backed clients are guarded.
    // JVM uses OkHttp so that guard's DNS is the one the connection uses.
    installPrivateNetworkGuard()

    install(ContentNegotiation) {
        json()
    }

    /*install(ContentEncoding) {
        deflate(1.0F)
        gzip(0.9F)
    }*/
}

/** JVM uses OkHttp so [installPrivateNetworkGuard] can pin the connection to the addresses it checked. */
internal expect fun oid4vcHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

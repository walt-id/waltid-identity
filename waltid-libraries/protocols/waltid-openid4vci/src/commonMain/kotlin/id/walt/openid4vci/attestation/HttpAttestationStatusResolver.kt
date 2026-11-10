package id.walt.openid4vci.attestation

import id.walt.webdatafetching.WebDataFetcher
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Embedding services own the fetcher and its lifecycle. Raw fetching bypasses its result cache;
 * shared URL restrictions, SSRF protection and response limits still apply. A deployment may
 * tighten the 2 MiB response cap, but cannot disable it or enable redirects for status lookups.
 */
class HttpAttestationStatusResolver(private val fetcher: WebDataFetcher) : AttestationStatusResolver {
    init {
        require(fetcher.dataFetcherConfiguration.engine?.followRedirects != true) { "Status-list redirects must be disabled" }
        require(fetcher.dataFetcherConfiguration.maxResponseBytes in 1..MAXIMUM_RESPONSE_BYTES) {
            "Status-list response limit must be positive and at most $MAXIMUM_RESPONSE_BYTES bytes"
        }
    }

    override suspend fun resolve(uri: String): String = withTimeoutOrNull(10_000) {
        val url = parseAttestationStatusListUrl(uri)
        val loopbackHost = url.host in setOf("localhost", "127.0.0.1", "::1") || url.host.endsWith(".localhost")
        require(url.user == null && url.password == null) { "Status-list URLs must not contain credentials" }
        require(url.protocol.name == "https" || url.protocol.name == "http" && loopbackHost) {
            "Status-list transport requires HTTPS; HTTP is permitted only on loopback for development"
        }
        val response = fetcher.rawFetch(url) {
            method = HttpMethod.Get
            // Attester URLs must never receive credentials inherited from generic request defaults.
            headers.clear()
            header("Accept", "application/statuslist+jwt")
            header("Cache-Control", "no-cache")
        }
        require(response.status.value in 200..299) { "Status-list lookup failed" }
        require(response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim().equals("application/statuslist+jwt", ignoreCase = true)) {
            "Unsupported status-list media type"
        }
        response.bodyAsText().trim()
    } ?: error("Status-list lookup timed out")

    companion object {
        const val MAXIMUM_RESPONSE_BYTES: Long = 2L * 1024 * 1024
    }
}

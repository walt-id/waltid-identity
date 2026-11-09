package id.walt.openid4vci.attestation

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull

/** Embedding services own the HTTP client. No acceptance-time cache or bearer credentials. */
class HttpAttestationStatusResolver(private val client: HttpClient) : AttestationStatusResolver {
    override suspend fun resolve(uri: String): String = withTimeoutOrNull(10_000) {
        val url = parseAttestationStatusListUrl(uri)
        val loopbackHost = url.host in setOf("localhost", "127.0.0.1", "::1") || url.host.endsWith(".localhost")
        require(url.protocol.name == "https" || url.protocol.name == "http" && loopbackHost) {
            "Status-list transport requires HTTPS; HTTP is permitted only on loopback for development"
        }
        val response = client.get(url) {
            header("Accept", "application/statuslist+jwt")
            header("Cache-Control", "no-cache")
        }
        require(response.status.value in 200..299) { "Status-list lookup failed" }
        require(response.headers["Content-Type"]?.substringBefore(';')?.trim() == "application/statuslist+jwt") {
            "Unsupported status-list media type"
        }
        response.bodyAsText().trim()
    } ?: error("Status-list lookup timed out")
}

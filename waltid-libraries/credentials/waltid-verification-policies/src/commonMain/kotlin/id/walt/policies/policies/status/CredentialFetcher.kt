package id.walt.policies.policies.status

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class CredentialFetcher(
    private val client: HttpClient
) {
    private val logger = KotlinLogging.logger { }

    suspend fun fetch(url: String): Result<String> = runCatching {
        logger.debug { "Fetching content from URL: $url" }
        download(url).bodyAsText()
    }.onFailure { logger.error { "Failed to fetch content from URL: $url" } }

    private suspend fun download(url: String): HttpResponse {
        val response = client.get(url) {
            headers {
                append(HttpHeaders.ContentType, "text/plain")
                append(HttpHeaders.Accept, "text/plain")
            }
        }
        return response.takeIf { it.status.isSuccess() }
            ?: throw IllegalStateException("URL $url returned unexpected status: ${response.status}")
    }
}
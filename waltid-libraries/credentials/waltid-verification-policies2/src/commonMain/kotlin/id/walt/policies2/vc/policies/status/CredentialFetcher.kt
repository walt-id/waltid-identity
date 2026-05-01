package id.walt.policies2.vc.policies.status

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Represents fetched status list content, supporting both text (JWT) and binary (CWT) formats.
 */
sealed class StatusListContent {
    /**
     * Text-based content (e.g., JWT status lists).
     */
    data class Text(val content: String) : StatusListContent()
    
    /**
     * Binary content (e.g., CWT status lists as raw CBOR bytes).
     */
    data class Binary(val content: ByteArray) : StatusListContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return content.contentEquals(other.content)
        }
        
        override fun hashCode(): Int = content.contentHashCode()
    }
}

class CredentialFetcher(
    private val client: HttpClient
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Fetches status list content from the given URL.
     * Returns [StatusListContent.Binary] for CWT content types and [StatusListContent.Text] for others.
     */
    suspend fun fetch(url: String): Result<StatusListContent> = runCatching {
        logger.debug { "Fetching content from URL: $url" }
        val response = download(url)
        val contentType = response.contentType()
        
        if (contentType?.match(ContentType("application", "statuslist+cwt")) == true) {
            logger.debug { "Received binary CWT content" }
            StatusListContent.Binary(response.readRawBytes())
        } else {
            logger.debug { "Received text content" }
            StatusListContent.Text(response.bodyAsText())
        }
    }.onFailure { logger.error { "Failed to fetch content from URL: $url" } }

    private suspend fun download(url: String): HttpResponse {
        val response = client.get(url) {
            headers {
                append(HttpHeaders.Accept, "application/statuslist+jwt, application/statuslist+cwt, text/plain, */*")
            }
        }
        return response.takeIf { it.status.isSuccess() }
            ?: throw IllegalStateException("URL $url returned unexpected status: ${response.status}")
    }
}

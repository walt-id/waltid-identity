package id.walt.policies2.vc.policies.status

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
        val response = download(url)
        val contentType = response.contentType()
        
        // Handle binary CWT content - convert to hex for internal processing
        if (contentType?.match(ContentType("application", "statuslist+cwt")) == true) {
            logger.debug { "Received CWT content, converting to hex" }
            response.readRawBytes().toHexString()
        } else {
            response.bodyAsText()
        }
    }.onFailure { logger.error { "Failed to fetch content from URL: $url" } }

    private suspend fun download(url: String): HttpResponse {
        val response = client.get(url) {
            headers {
                // Accept both text and binary status list formats
                append(HttpHeaders.Accept, "application/statuslist+jwt, application/statuslist+cwt, text/plain, */*")
            }
        }
        return response.takeIf { it.status.isSuccess() }
            ?: throw IllegalStateException("URL $url returned unexpected status: ${response.status}")
    }
    
    private fun ByteArray.toHexString(): String = joinToString("") { byte ->
        val hex = (byte.toInt() and 0xFF).toString(16)
        if (hex.length == 1) "0$hex" else hex
    }
}

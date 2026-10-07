package id.walt.credentials.jsonld

import id.walt.dcql.DcqlCredential
import id.walt.dcql.jsonld.JsonLdContextDocumentSource
import id.walt.dcql.jsonld.W3cTypeExpander
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

private val log = KotlinLogging.logger {}

/**
 * Fetches JSON-LD context documents referenced by [credentials].
 *
 * `Accept: application/ld+json` is required: some context URLs, including Gaia-X, return HTML
 * unless the client asks for a JSON-LD document. Documents that cannot be loaded are omitted
 * and those type terms stay unexpanded.
 */
suspend fun HttpClient.loadW3cContextDocuments(
    credentials: List<DcqlCredential>,
): JsonLdContextDocumentSource = W3cTypeExpander.resolveDocuments(credentials) { url ->
    try {
        val response = get(url) {
            header(HttpHeaders.Accept, "application/ld+json, application/json;q=0.5")
        }
        if (!response.status.isSuccess()) {
            log.warn { "JSON-LD context $url responded ${response.status}" }
            null
        } else {
            response.bodyAsText()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        log.warn { "Failed to fetch JSON-LD context $url: ${error.message}" }
        null
    }
}

package id.walt.policies.policies.status

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Fetches IETF / W3C status-list tokens.
 *
 * Binary Token Status List CWTs (`application/statuslist+cwt`, or a COSE_Sign1 body) are
 * returned as hex so the existing string-based CWT reader/parser can decode them. JWT and
 * other text status lists stay as UTF-8.
 */
class CredentialFetcher(
    private val client: HttpClient
) {
    private val logger = KotlinLogging.logger { }

    suspend fun fetch(url: String): Result<String> = runCatching {
        logger.debug { "Fetching content from URL: $url" }
        decodeStatusListBody(download(url))
    }.onFailure { logger.error { "Failed to fetch content from URL: $url" } }

    private suspend fun download(url: String): HttpResponse {
        val response = client.get(url) {
            headers {
                append(HttpHeaders.Accept, "$STATUS_LIST_JWT, $STATUS_LIST_CWT, text/plain, */*")
            }
        }
        return response.takeIf { it.status.isSuccess() }
            ?: throw IllegalStateException("URL $url returned unexpected status: ${response.status}")
    }

    companion object {
        private val STATUS_LIST_JWT = ContentType("application", "statuslist+jwt")
        private val STATUS_LIST_CWT = ContentType("application", "statuslist+cwt")
        private const val COSE_SIGN1_TAG: Byte = 0xD2.toByte()
        private const val CBOR_ARRAY_4: Byte = 0x84.toByte()

        internal suspend fun decodeStatusListBody(response: HttpResponse): String {
            val bytes = response.readRawBytes()
            return if (isCwt(response.contentType(), bytes)) {
                bytes.toHexString()
            } else {
                bytes.decodeToString()
            }
        }

        /**
         * Content type wins. When it is missing or generic, a leading COSE_Sign1 tag (`0xD2`)
         * or an untagged 4-element CBOR array (`0x84`) is treated as CWT. Any other body is
         * decoded as text; this sniff is only a fallback for a mislabelled binary list.
         */
        internal fun isCwt(contentType: ContentType?, bytes: ByteArray): Boolean {
            if (contentType?.match(STATUS_LIST_CWT) == true) return true
            if (contentType?.match(STATUS_LIST_JWT) == true) return false
            return bytes.isNotEmpty() && (bytes[0] == COSE_SIGN1_TAG || bytes[0] == CBOR_ARRAY_4)
        }
    }
}

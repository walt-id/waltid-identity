package id.walt.ades.transport

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Network access used to fetch time-stamps, OCSP responses, CRLs, issuer certificates and sigD-referenced
 * documents. Abstracted so callers can plug caching, proxies, authentication or offline fixtures.
 */
interface AdesTransport {
    suspend fun get(url: String): ByteArray
    suspend fun post(url: String, contentType: String, body: ByteArray, headers: Map<String, String> = emptyMap()): ByteArray
}

class AdesTransportException(message: String) : RuntimeException(message)

/** Ktor-based transport; the caller owns the [client] and its engine. */
class KtorAdesTransport(private val client: HttpClient) : AdesTransport {
    override suspend fun get(url: String): ByteArray = client.get(url).bodyOrThrow(url)

    override suspend fun post(url: String, contentType: String, body: ByteArray, headers: Map<String, String>): ByteArray =
        client.post(url) {
            contentType(ContentType.parse(contentType))
            headers { headers.forEach { (k, v) -> append(k, v) }; append(HttpHeaders.Accept, "*/*") }
            setBody(body)
        }.bodyOrThrow(url)

    private suspend fun HttpResponse.bodyOrThrow(url: String): ByteArray {
        if (!status.isSuccess()) throw AdesTransportException("HTTP ${status.value} from $url")
        return readRawBytes()
    }
}

/** Transport that refuses all network access (offline validation). */
object OfflineAdesTransport : AdesTransport {
    override suspend fun get(url: String): ByteArray = throw AdesTransportException("offline: GET $url")
    override suspend fun post(url: String, contentType: String, body: ByteArray, headers: Map<String, String>): ByteArray =
        throw AdesTransportException("offline: POST $url")
}

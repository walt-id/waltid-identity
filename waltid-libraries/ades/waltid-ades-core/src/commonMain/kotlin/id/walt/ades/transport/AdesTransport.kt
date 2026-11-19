package id.walt.ades.transport

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.pluginOrNull
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
 * URLs can originate in untrusted signatures. Implementations must enforce the application's outbound
 * URL policy, including redirects and DNS resolution, before accessing any network resource.
 */
interface AdesTransport {
    suspend fun get(url: String): ByteArray
    suspend fun post(url: String, contentType: String, body: ByteArray, headers: Map<String, String> = emptyMap()): ByteArray
}

class AdesTransportException(message: String) : RuntimeException(message)

/**
 * Ktor-based transport; the caller owns the [client] and its engine. [checkUrl] must reject disallowed
 * destinations before either GET or POST. Use a DNS-aware outbound guard for server applications.
 * Automatic redirects must be disabled so they cannot bypass [checkUrl].
 */
class KtorAdesTransport(private val client: HttpClient, private val checkUrl: (String) -> Unit) : AdesTransport {
    init {
        require(client.pluginOrNull(HttpRedirect) == null) { "Disable automatic redirects on the AdES HTTP client" }
    }

    override suspend fun get(url: String): ByteArray {
        checkUrl(url)
        return client.get(url).bodyOrThrow(url)
    }

    override suspend fun post(url: String, contentType: String, body: ByteArray, headers: Map<String, String>): ByteArray {
        checkUrl(url)
        return client.post(url) {
            contentType(ContentType.parse(contentType))
            headers { headers.forEach { (k, v) -> append(k, v) }; append(HttpHeaders.Accept, "*/*") }
            setBody(body)
        }.bodyOrThrow(url)
    }

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

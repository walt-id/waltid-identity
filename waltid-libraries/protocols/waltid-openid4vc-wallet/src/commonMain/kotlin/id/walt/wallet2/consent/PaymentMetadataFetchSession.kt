package id.walt.wallet2.consent

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA384
import org.kotlincrypto.hash.sha2.SHA512
import kotlin.io.encoding.Base64

/** One bounded resolution snapshot; retrieved bytes are never reused across reviews. */
internal class PaymentMetadataFetchSession(
    httpClient: HttpClient,
    private val allowHttpLoopback: Boolean = false,
    private val requireUrlAllowed: (String) -> Unit = {},
) : AutoCloseable {
    private val client = httpClient.config { followRedirects = false; expectSuccess = false }
    private val documents = mutableMapOf<String, ByteArray>()
    private var requests = 0

    suspend fun read(uri: String, integrity: List<String> = emptyList()): JsonElement = try {
        val bytes = documents[uri] ?: withTimeoutOrNull(10_000L) {
            if (documents.size >= MAX_DOCUMENTS) consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
            retrieve(uri).also { documents[uri] = it }
        } ?: consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
        integrity.forEach { verifyMetadataIntegrity(bytes, it) }
        Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true))
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: PaymentConsentException) {
        throw cause
    } catch (_: Exception) {
        consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
    }

    private suspend fun retrieve(originalUri: String): ByteArray {
        var uri = checkedUrl(originalUri)
        repeat(MAX_REDIRECTS + 1) { redirects ->
            if (++requests > MAX_REQUESTS) consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
            var redirect: Url? = null
            val bytes = client.prepareGet(uri) { headers.append(HttpHeaders.Accept, "application/json") }.execute { response ->
                if (response.status.value in setOf(301, 302, 303, 307, 308)) {
                    if (redirects == MAX_REDIRECTS) consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
                    val location = response.headers[HttpHeaders.Location] ?: consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
                    redirect = checkedUrl(URLBuilder(uri).takeFrom(location).apply {
                        encodedPath = removeDotSegments(encodedPath)
                    }.buildString())
                    return@execute null
                }
                if (!response.status.isSuccess()) consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
                if ((response.contentLength() ?: 0) > MAX_BYTES) consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
                val data = response.bodyAsChannel().readRemaining(MAX_BYTES + 1L).readByteArray()
                if (data.size > MAX_BYTES) consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
                data
            }
            if (bytes != null) return bytes
            uri = requireNotNull(redirect)
        }
        consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
    }

    private fun checkedUrl(value: String): Url {
        val url = Url(value)
        val local = allowHttpLoopback && url.protocol == URLProtocol.HTTP && url.host in setOf("localhost", "127.0.0.1", "[::1]", "::1")
        if ((url.protocol != URLProtocol.HTTPS && !local) || url.host.isBlank() ||
            url.user != null || url.password != null || url.fragment.isNotEmpty()) {
            consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE)
        }
        requireUrlAllowed(url.toString())
        return url
    }

    override fun close() { client.close(); documents.clear() }

    private companion object {
        const val MAX_DOCUMENTS = 3 // VCT, claims, catalogue.
        const val MAX_REDIRECTS = 3
        const val MAX_REQUESTS = MAX_DOCUMENTS * (MAX_REDIRECTS + 1)
        const val MAX_BYTES = 262_144
    }
}

/** SRI parsing/algorithm agility, with wallet policy requiring at least one supported digest. */
internal fun verifyMetadataIntegrity(bytes: ByteArray, reference: String) {
    val tokens = reference.trim().split(Regex("\\s+"))
    if (tokens.isEmpty() || tokens.size > 8) consentFailure(PaymentConsentFailure.INVALID_METADATA)
    val syntax = Regex("(sha256|sha384|sha512)-([A-Za-z0-9+/_-]+={0,2})(?:\\?[\\x21-\\x7E]*)?")
    val digests = tokens.mapNotNull { token ->
        // SRI 2016 sections 3.3.3 and 3.5: skip invalid/unknown tokens and ignore unknown options.
        val match = syntax.matchEntire(token) ?: return@mapNotNull null
        val algorithm = match.groupValues[1]
        val size = when (algorithm) {
            "sha256" -> 32
            "sha384" -> 48
            "sha512" -> 64
            else -> consentFailure(PaymentConsentFailure.INVALID_METADATA)
        }
        val encoded = match.groupValues[2].replace('-', '+').replace('_', '/')
        val expected = try {
            Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(encoded)
        } catch (_: IllegalArgumentException) { null }
        size to expected
    }
    // A supplied pin without any usable algorithm must not silently become unpinned payment metadata.
    if (digests.isEmpty()) consentFailure(PaymentConsentFailure.INVALID_METADATA)
    val size = digests.maxOf { it.first }
    val actual = when (size) {
        32 -> SHA256().digest(bytes)
        48 -> SHA384().digest(bytes)
        else -> SHA512().digest(bytes)
    }
    if (digests.filter { it.first == size }.none { (_, expected) -> expected != null && actual.contentEquals(expected) }) {
        consentFailure(PaymentConsentFailure.INTEGRITY_MISMATCH)
    }
}

/** RFC 3986 section 5.2.4, operating on encoded segments so escaped dots keep their meaning. */
private fun removeDotSegments(path: String): String {
    val segments = mutableListOf<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "." -> Unit
            ".." -> if (segments.size > 1) segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
    }
    if (path.endsWith("/.") || path.endsWith("/..")) segments.add("")
    return segments.joinToString("/").ifEmpty { "/" }
}

package id.walt.webdatafetching

import id.walt.webdatafetching.config.RequestConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray

/** A bounded operation snapshot. Redirects never forward origin credentials to a different origin. */
class BoundedDocumentSession(
    httpClient: HttpClient,
    private val requestConfiguration: RequestConfiguration? = null,
    private val requireUrlAllowed: (String) -> Unit = {},
    private val allowHttp: Boolean = false,
    initialCredentialOrigin: String? = null,
) : AutoCloseable {
    constructor(fetcher: WebDataFetcher, allowHttp: Boolean = false, initialCredentialOrigin: String? = null) : this(
        fetcher.httpClient, fetcher.dataFetcherConfiguration.request,
        { uri -> fetcher.dataFetcherConfiguration.url?.requireUrlAllowed(uri) }, allowHttp, initialCredentialOrigin,
    )

    private val client = httpClient.config { followRedirects = false; expectSuccess = false }
    private val documents = mutableMapOf<String, ByteArray>()
    private var credentialOrigin: Url? = initialCredentialOrigin?.let(::Url)
    private var requests = 0
    private val started = kotlin.time.TimeSource.Monotonic.markNow()

    suspend fun read(uri: String): ByteArray = try {
        val bytes = documents[uri] ?: withTimeoutOrNull(minOf(10_000L, (30_000L - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(1L))) {
            if (documents.size >= MAX_DOCUMENTS) throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
            retrieve(uri).also {
                require(documents.values.sumOf { value -> value.size.toLong() } + it.size <= MAX_TOTAL_BYTES) { "Metadata byte budget exceeded" }
                documents[uri] = it
            }
        } ?: throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
        bytes.copyOf()
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
    }

    private suspend fun retrieve(originalUri: String): ByteArray {
        val origin = checkedUrl(originalUri)
        val configuredOrigin = credentialOrigin ?: origin.also { credentialOrigin = it }
        var uri = origin
        repeat(MAX_REDIRECTS + 1) { redirects ->
            require(started.elapsedNow().inWholeMilliseconds < 30_000L) { "Metadata time budget exceeded" }
            if (++requests > MAX_REQUESTS) throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
            var redirect: Url? = null
            val bytes = client.prepareGet(uri) {
                // Configuration belongs to the requested origin, not an arbitrary redirect target.
                if (uri.protocol == configuredOrigin.protocol && uri.host == configuredOrigin.host && uri.port == configuredOrigin.port) {
                    requestConfiguration?.applyConfiguration(this)
                }
                method = HttpMethod.Get
                if (!headers.contains(HttpHeaders.Accept)) headers.append(HttpHeaders.Accept, "application/json")
            }.execute { response ->
                if (response.status.value in setOf(301, 302, 303, 307, 308)) {
                    if (redirects == MAX_REDIRECTS) throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
                    val location = response.headers[HttpHeaders.Location] ?: throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
                    redirect = checkedUrl(URLBuilder(uri).takeFrom(location).apply {
                        encodedPath = removeDotSegments(encodedPath)
                    }.buildString())
                    return@execute null
                }
                if (!response.status.isSuccess()) throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
                if ((response.contentLength() ?: 0) > MAX_BYTES) throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
                val data = response.bodyAsChannel().readBuffer(MAX_BYTES + 1L).readByteArray()
                if (data.size > MAX_BYTES) throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
                data
            }
            if (bytes != null) return bytes
            uri = requireNotNull(redirect)
        }
        throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
    }

    private fun checkedUrl(value: String): Url {
        val url = Url(value)
        if ((url.protocol != URLProtocol.HTTPS && !(allowHttp && url.protocol == URLProtocol.HTTP)) || url.host.isBlank() ||
            url.user != null || url.password != null || url.fragment.isNotEmpty()) {
            throw IllegalArgumentException("Metadata document unavailable or retrieval limit exceeded")
        }
        requireUrlAllowed(url.toString())
        return url
    }

    override fun close() { client.close(); documents.clear() }

    private companion object {
        const val MAX_DOCUMENTS = 16 // Type chain plus bounded ancillary documents.
        const val MAX_REDIRECTS = 3
        const val MAX_REQUESTS = MAX_DOCUMENTS * (MAX_REDIRECTS + 1)
        const val MAX_TOTAL_BYTES = 2_097_152
        const val MAX_BYTES = 262_144
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

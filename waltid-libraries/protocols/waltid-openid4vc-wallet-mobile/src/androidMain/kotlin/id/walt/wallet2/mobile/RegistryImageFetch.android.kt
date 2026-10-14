package id.walt.wallet2.mobile

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.io.readByteArray

internal suspend fun fetchRegistryIconBytes(url: String): ByteArray? =
    fetchRegistryIconBytes(url, RegistryImageHttp.client)

internal suspend fun fetchRegistryIconBytes(url: String, client: HttpClient): ByteArray? {
    if (!isHttpsUrl(url)) return null
    return try {
        client.prepareGet(url).execute { response ->
            try {
                if (!response.status.isSuccess()) return@execute null
                val contentLength = response.contentLength()
                if (contentLength != null && contentLength > MaxRegistryIconBytes) return@execute null
                val bytes = response.bodyAsChannel().readBuffer(MaxRegistryIconBytes + 1L).readByteArray()
                if (bytes.size > MaxRegistryIconBytes) return@execute null
                bytes
            } finally {
                // Interrupt the socket reader even when an early return leaves the body unread.
                response.cancel()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private object RegistryImageHttp {
    val client: HttpClient = createRegistryImageHttpClient()
}

internal fun createRegistryImageHttpClient(configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient =
    // Ktor's Android engine closes RawSourceChannel during a blocking read on cancellation.
    // HttpURLConnection can then drain the same source concurrently (KTOR-9848). OkHttp cancels
    // the call/socket first and lets its reader unwind before response-body cleanup.
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = RegistryIconFetchTimeoutMs
            connectTimeoutMillis = RegistryIconFetchTimeoutMs
            socketTimeoutMillis = RegistryIconFetchTimeoutMs
        }
        configure()
    }

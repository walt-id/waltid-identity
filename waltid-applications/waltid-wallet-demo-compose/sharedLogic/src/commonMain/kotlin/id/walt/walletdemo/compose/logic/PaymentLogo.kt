package id.walt.walletdemo.compose.logic

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlin.io.encoding.Base64

/** A URI can be valid transaction data without being an appropriate external navigation target. */
fun paymentWebsite(value: String): String? = runCatching {
    val url = Url(value)
    value.takeIf { url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() &&
        url.user.isNullOrEmpty() && url.password.isNullOrEmpty() && value.none { it.isWhitespace() || it.code < 32 } }
}.getOrNull()

private const val MAX_LOGO_BYTES = 524_288

/** No cookies, credentials, redirects or automatic remote fetches. Image decoding stays with the renderer. */
suspend fun loadPaymentLogo(value: String, allowRemote: Boolean = false): ByteArray? =
    loadPaymentLogo(value, allowRemote, ::paymentLogoHttpClient)

internal suspend fun loadPaymentLogo(value: String, allowRemote: Boolean, createClient: () -> HttpClient?): ByteArray? {
    try {
        val bytes = if (value.startsWith("data:")) {
            val header = value.substringBefore(',')
            if (header !in setOf("data:image/png;base64", "data:image/jpeg;base64") ||
                value.length > MAX_LOGO_BYTES * 4 / 3 + 64) return null
            Base64.Default.decode(value.substringAfter(','))
        } else {
            if (!allowRemote || paymentWebsite(value) == null) return null
            val host = Url(value).host.lowercase()
            if (host == "localhost" || host.endsWith(".local") || ':' in host || host.all { it.isDigit() || it == '.' }) return null
            val client = createClient() ?: return null
            try {
                withTimeoutOrNull(5_000) {
                    client.prepareGet(value) { headers.append(HttpHeaders.Accept, "image/png, image/jpeg") }.execute { response ->
                        if (response.status.value !in 200..299 || (response.contentLength() ?: 0) > MAX_LOGO_BYTES) return@execute null
                        response.bodyAsChannel().readBuffer(MAX_LOGO_BYTES + 1L).readByteArray()
                    }
                } ?: return null
            } finally { client.close() }
        }
        val png = bytes.size >= 8 && bytes.take(8).map { it.toInt() and 255 } == listOf(137, 80, 78, 71, 13, 10, 26, 10)
        val jpeg = bytes.size >= 3 && bytes.take(3).map { it.toInt() and 255 } == listOf(255, 216, 255)
        return bytes.takeIf { it.size in 1..MAX_LOGO_BYTES && (png || jpeg) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return null
    }
}

/** Dedicated platform client without shared cookie or credential storage; null where logos are unsupported. */
internal expect fun paymentLogoHttpClient(): HttpClient?

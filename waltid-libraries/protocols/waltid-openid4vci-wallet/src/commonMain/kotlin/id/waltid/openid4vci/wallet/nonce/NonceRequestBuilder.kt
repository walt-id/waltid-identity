package id.waltid.openid4vci.wallet.nonce

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Stable failure categories for an OpenID4VCI Nonce Endpoint request. */
enum class NonceRequestError {
    INVALID_ENDPOINT,
    NETWORK,
    ISSUER_RESPONSE,
    INVALID_RESPONSE,
}

/** Sanitized nonce-endpoint failure that never retains response or nonce material. */
class NonceRequestException internal constructor(
    val error: NonceRequestError,
    val statusCode: Int? = null,
) : Exception(
    buildString {
        append("Nonce request failed: ")
        append(error.name.lowercase())
        statusCode?.let { append(" (HTTP ").append(it).append(')') }
    },
)

/** OpenID4VCI 1.0 Nonce Endpoint response. */
@Serializable
data class NonceResponse(
    @SerialName("c_nonce") val cNonce: String,
) {
    override fun toString(): String = "NonceResponse(cNonce=<redacted>)"
}

/**
 * Obtains fresh credential-proof nonces from the issuer's advertised Nonce Endpoint.
 */
class NonceRequestBuilder(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestNonce(nonceEndpoint: String): NonceResponse {
        validateEndpoint(nonceEndpoint)
        val response = send(nonceEndpoint)

        if (!response.status.isSuccess()) {
            throw NonceRequestException(NonceRequestError.ISSUER_RESPONSE, response.status.value)
        }

        val responseBody = try {
            response.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw NonceRequestException(NonceRequestError.NETWORK)
        }
        val parsed = try {
            json.decodeFromString<NonceResponse>(responseBody)
        } catch (_: Exception) {
            throw NonceRequestException(NonceRequestError.INVALID_RESPONSE)
        }
        if (parsed.cNonce.isBlank()) {
            throw NonceRequestException(NonceRequestError.INVALID_RESPONSE)
        }
        return parsed
    }

    private suspend fun send(endpoint: String): HttpResponse = try {
        httpClient.post(endpoint) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        throw NonceRequestException(NonceRequestError.NETWORK)
    }

    private fun validateEndpoint(endpoint: String) {
        val url = try {
            Url(endpoint)
        } catch (_: Exception) {
            throw NonceRequestException(NonceRequestError.INVALID_ENDPOINT)
        }
    }
}

package id.waltid.openid4vci.wallet.attestation

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Stable failure categories for an OAuth client-attestation challenge request. */
enum class WalletAttestationChallengeRequestError {
    INVALID_ENDPOINT,
    NETWORK,
    AUTHORIZATION_SERVER_RESPONSE,
    INVALID_RESPONSE,
}

/** Sanitized challenge-endpoint failure that never retains response or challenge material. */
class WalletAttestationChallengeRequestException internal constructor(
    val error: WalletAttestationChallengeRequestError,
    val statusCode: Int? = null,
) : Exception(
    buildString {
        append("Client attestation challenge request failed: ")
        append(error.name.lowercase())
        statusCode?.let { append(" (HTTP ").append(it).append(')') }
    },
)

/** Response returned by the OAuth attestation challenge endpoint. */
@Serializable
data class WalletAttestationChallengeResponse(
    @SerialName("attestation_challenge")
    val attestationChallenge: String,
) {
    override fun toString(): String = "WalletAttestationChallengeResponse(attestationChallenge=<redacted>)"
}

/** Fetches a server-provided challenge for the next Client Attestation PoP JWT. */
class WalletAttestationChallengeRequestBuilder(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestChallenge(endpoint: String): WalletAttestationChallengeResponse {
        validateEndpoint(endpoint)
        val response = send(endpoint)
        if (response.status != HttpStatusCode.OK) {
            throw WalletAttestationChallengeRequestException(
                WalletAttestationChallengeRequestError.AUTHORIZATION_SERVER_RESPONSE,
                response.status.value,
            )
        }

        val body = try {
            response.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw WalletAttestationChallengeRequestException(
                WalletAttestationChallengeRequestError.NETWORK,
            )
        }
        val parsed = try {
            json.decodeFromString<WalletAttestationChallengeResponse>(body)
        } catch (_: Exception) {
            throw WalletAttestationChallengeRequestException(
                WalletAttestationChallengeRequestError.INVALID_RESPONSE,
            )
        }
        if (parsed.attestationChallenge.isBlank()) {
            throw WalletAttestationChallengeRequestException(
                WalletAttestationChallengeRequestError.INVALID_RESPONSE,
            )
        }
        return parsed
    }

    private suspend fun send(endpoint: String): HttpResponse = try {
        httpClient.post(endpoint) {
            accept(ContentType.Application.Json)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        throw WalletAttestationChallengeRequestException(
            WalletAttestationChallengeRequestError.NETWORK,
        )
    }

    private fun validateEndpoint(endpoint: String) {
        try {
            require(endpoint.isNotBlank())
            require(Url(endpoint).host.isNotBlank())
        } catch (_: Exception) {
            throw WalletAttestationChallengeRequestException(
                WalletAttestationChallengeRequestError.INVALID_ENDPOINT,
            )
        }
    }
}

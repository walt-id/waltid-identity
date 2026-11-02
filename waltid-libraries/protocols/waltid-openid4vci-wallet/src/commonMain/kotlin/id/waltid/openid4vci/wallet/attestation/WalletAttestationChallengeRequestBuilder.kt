package id.waltid.openid4vci.wallet.attestation

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.retry
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

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

/** Local result, not a JSON wire model. The two freshness values are independent. */
data class WalletAttestationChallengeResult(
    val attestationChallenge: String?,
    val dpopNonce: String?,
    val prepared: Boolean = false,
) {
    override fun toString(): String = "WalletAttestationChallengeResult(<redacted>)"
}

/** Fetches a server-provided challenge for the next Client Attestation PoP JWT. */
class WalletAttestationChallengeRequestBuilder(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestChallenge(endpoint: String): WalletAttestationChallengeResponse =
        WalletAttestationChallengeResponse(requireNotNull(requestChallengeDetailed(endpoint).attestationChallenge))

    suspend fun requestChallengeDetailed(endpoint: String): WalletAttestationChallengeResult {
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
        val nonce = response.headers[DPOP_NONCE_HEADER]?.takeIf(String::isNotBlank)
        val challenge = try {
            val bodyObject = json.parseToJsonElement(body).jsonObject
            val value = bodyObject["attestation_challenge"]
            if (value == null) null else {
                require(value is JsonPrimitive && value.isString && value.content.isNotBlank())
                value.content
            }
        } catch (_: Exception) {
            throw WalletAttestationChallengeRequestException(WalletAttestationChallengeRequestError.INVALID_RESPONSE)
        }
        if (challenge == null) {
            throw WalletAttestationChallengeRequestException(WalletAttestationChallengeRequestError.INVALID_RESPONSE)
        }
        return WalletAttestationChallengeResult(challenge, nonce)
    }

    private suspend fun send(endpoint: String): HttpResponse = try {
        httpClient.post(endpoint) {
            expectSuccess = false
            retry { noRetry() }
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

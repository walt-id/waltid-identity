package id.waltid.openid4vci.wallet.authorization

import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.clientauth.CLIENT_ASSERTION_TYPE_JWT_BEARER
import id.waltid.openid4vci.wallet.token.ClientAssertionFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.serialization.json.Json

/** The pushed authorization request endpoint answered with a status that may succeed on retry. */
class RetryablePushedAuthorizationRequestException(val statusCode: Int) :
    IllegalStateException("Pushed authorization request failed with a retryable HTTP $statusCode")

/**
 * Executes an RFC 9126 pushed authorization request.
 *
 * Single place that knows the PAR endpoint's HTTP contract, so the server-side and session-service
 * authorization flows cannot drift apart on status handling or client authentication.
 *
 * The endpoint is client-authenticated (RFC 9126 Section 2, FAPI 2.0 Security Profile Section
 * 5.3.2.1), so [clientAssertionFactory] and [attestationHeaders] carry the same authentication the
 * token endpoint uses. Omitting them made the suite reject the request even once PAR itself was
 * performed.
 */
object PushedAuthorizationRequestExecutor {

    private val log = KotlinLogging.logger { }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * POST [parameters] to [parEndpoint] and return the parsed response.
     *
     * @param clientAssertionFactory invoked once per call so each request carries a fresh `jti`
     *   (RFC 7523 Section 3); a replayed assertion is rejected as reuse.
     */
    suspend fun execute(
        httpClient: HttpClient,
        parEndpoint: String,
        parameters: Map<String, String>,
        clientAssertionFactory: ClientAssertionFactory? = null,
        attestationHeaders: ClientAttestationHeaders? = null,
    ): PushedAuthorizationResponse {
        val body = Parameters.build {
            parameters.forEach { (name, value) -> append(name, value) }
            clientAssertionFactory?.let { factory ->
                append("client_assertion_type", CLIENT_ASSERTION_TYPE_JWT_BEARER)
                append("client_assertion", factory())
            }
        }.formUrlEncode()

        log.trace { "Pushing authorization request to $parEndpoint (${parameters.size} parameters)" }
        val response = httpClient.post(parEndpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
            attestationHeaders?.let { headers ->
                header(ClientAttestationHeaders.HEADER_ATTESTATION, headers.attestationJwt)
                header(ClientAttestationHeaders.HEADER_ATTESTATION_POP, headers.popJwt)
            }
        }

        when {
            // RFC 9126 Section 2.2: a successful pushed authorization response is 201 Created.
            response.status == HttpStatusCode.Created -> Unit

            response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500 ->
                throw RetryablePushedAuthorizationRequestException(response.status.value)

            else -> throw IllegalArgumentException(
                "Pushed authorization request failed with HTTP ${response.status.value}"
            )
        }

        return json.decodeFromString<PushedAuthorizationResponse>(response.bodyAsText())
    }
}

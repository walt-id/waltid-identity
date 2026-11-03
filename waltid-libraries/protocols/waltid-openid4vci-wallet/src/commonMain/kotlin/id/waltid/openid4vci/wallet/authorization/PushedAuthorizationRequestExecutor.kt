package id.waltid.openid4vci.wallet.authorization

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.retry
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.waltid.openid4vci.wallet.attestation.ClientAttestationChallengeRetry
import id.waltid.openid4vci.wallet.token.ClientAttestationHeadersFactory
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
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
     * @param onResponseHeaders invoked before status handling so challenge headers from error
     *   responses can still be persisted.
     */
    suspend fun execute(
        httpClient: HttpClient,
        parEndpoint: String,
        parameters: Map<String, String>,
        clientAssertionFactory: ClientAssertionFactory? = null,
        attestationHeaders: ClientAttestationHeaders? = null,
        onResponseHeaders: suspend (Headers) -> Unit = {},
    ): PushedAuthorizationResponse = executeRequest(
        httpClient, parEndpoint, parameters, clientAssertionFactory, attestationHeaders, null, onResponseHeaders,
    )

    /** Rebuilds the PoP after observing a replacement challenge; keeps released overloads intact. */
    suspend fun execute(
        httpClient: HttpClient,
        parEndpoint: String,
        parameters: Map<String, String>,
        clientAssertionFactory: ClientAssertionFactory? = null,
        onResponseHeaders: suspend (Headers) -> Unit = {},
        attestationHeadersFactory: ClientAttestationHeadersFactory?,
    ): PushedAuthorizationResponse = executeRequest(
        httpClient, parEndpoint, parameters, clientAssertionFactory, null, attestationHeadersFactory, onResponseHeaders,
    )

    private suspend fun executeRequest(
        httpClient: HttpClient,
        parEndpoint: String,
        parameters: Map<String, String>,
        clientAssertionFactory: ClientAssertionFactory?,
        staticHeaders: ClientAttestationHeaders?,
        headersFactory: ClientAttestationHeadersFactory?,
        onResponseHeaders: suspend (Headers) -> Unit,
    ): PushedAuthorizationResponse {
        val originalParameters = Parameters.build { parameters.forEach { (name, value) -> append(name, value) } }
        val recovery = ClientAttestationChallengeRetry()
        repeat(2) {
            val body = Parameters.build {
                appendAll(originalParameters)
                clientAssertionFactory?.let { factory ->
                    append("client_assertion_type", CLIENT_ASSERTION_TYPE_JWT_BEARER)
                    append("client_assertion", factory())
                }
            }.formUrlEncode()
            val authentication = headersFactory?.invoke() ?: staticHeaders
            log.trace { "Pushing authorization request (${parameters.size} parameters)" }
            val response = httpClient.post(parEndpoint) {
                expectSuccess = false
                retry { noRetry() }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body)
                authentication?.let {
                    header(ClientAttestationHeaders.HEADER_ATTESTATION, it.attestationJwt)
                    header(ClientAttestationHeaders.HEADER_ATTESTATION_POP, it.popJwt)
                }
            }
            onResponseHeaders(response.headers)
            if (response.status == HttpStatusCode.Created) {
                return json.decodeFromString<PushedAuthorizationResponse>(response.bodyAsText())
            }
            val error = if (response.status == HttpStatusCode.BadRequest && headersFactory != null) {
                try {
                    json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { null }
            } else null
            if (recovery.shouldRetry(response.status, error, response.headers, headersFactory != null)) return@repeat
            if (response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500) {
                throw RetryablePushedAuthorizationRequestException(response.status.value)
            }
            throw IllegalArgumentException("Pushed authorization request failed with HTTP ${response.status.value}")
        }
        error("Pushed authorization request retry exhausted")
    }
}

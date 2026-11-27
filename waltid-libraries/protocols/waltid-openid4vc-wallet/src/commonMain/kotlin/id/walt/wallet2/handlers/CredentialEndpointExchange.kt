package id.walt.wallet2.handlers

import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.proofs.Proofs
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.waltid.openid4vci.wallet.credential.CredentialEncryption
import id.waltid.openid4vci.wallet.credential.CredentialIssuanceTarget
import id.waltid.openid4vci.wallet.credential.CredentialRequestBuilder
import id.waltid.openid4vci.wallet.credential.PreparedCredentialRequest
import id.waltid.openid4vci.wallet.credential.acceptCredentialResponse
import id.waltid.openid4vci.wallet.credential.isApplicationJwt
import id.waltid.openid4vci.wallet.credential.validateCredentialResponse
import id.waltid.openid4vci.wallet.dpop.DPOP_HEADER
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_ATTEMPTS
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import id.waltid.openid4vci.wallet.dpop.USE_DPOP_NONCE
import id.waltid.openid4vci.wallet.nonce.NonceRequestBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

internal data class CredentialEndpointExchange(
    val response: CredentialResponse,
    val responseDecryptionKey: Crypto2Key? = null,
)

/**
 * Fetches a credential with a freshly generated proof, retrying once only when the issuer
 * explicitly rejects that proof with the OID4VCI `invalid_nonce` error.
 *
 * Isolated fetch callers deliberately do not use this helper: they own the separate
 * request-nonce and sign-proof steps and therefore must handle [CredentialEndpointException]
 * themselves.
 */
internal suspend fun exchangeCredentialWithNonceRetry(
    request: FetchCredentialRequest,
    nonceEndpoint: String?,
    httpClient: HttpClient,
    buildProof: (suspend (String?) -> Proofs?)?,
    onProofGenerated: suspend () -> Unit = {},
    dpop: DpopRequestContext? = null,
    issuerMetadata: CredentialIssuerMetadata? = null,
    onStage: (CredentialIssuanceStage) -> Unit = {},
): CredentialEndpointExchange {
    suspend fun fetchWithFreshProof(): CredentialEndpointExchange {
        val proofs = buildProof?.let {
            val nonce = requestProofNonce(httpClient, nonceEndpoint)
            onStage(CredentialIssuanceStage.PROOF)
            it(nonce)
        }
        onProofGenerated()
        return requestCredential(request.copy(proofs = proofs), httpClient, dpop, issuerMetadata, onStage)
    }

    return try {
        fetchWithFreshProof()
    } catch (error: CredentialEndpointException) {
        if (!error.isInvalidNonce || buildProof == null || nonceEndpoint == null) {
            throw error
        }
        log.info { "Credential issuer rejected the proof nonce; obtaining a fresh nonce and retrying once" }
        fetchWithFreshProof()
    }
}

internal suspend fun requestCredential(
    request: FetchCredentialRequest,
    httpClient: HttpClient,
    dpop: DpopRequestContext? = null,
    issuerMetadata: CredentialIssuerMetadata? = null,
    onStage: (CredentialIssuanceStage) -> Unit = {},
): CredentialEndpointExchange {
    val credentialRequestJson = CredentialRequestBuilder.build(
        CredentialIssuanceTarget(request.credentialConfigurationId, request.credentialIdentifier), request.effectiveProofs?.jwt)
    val prepared = CredentialEncryption.prepare(credentialRequestJson, issuerMetadata)
    val response = httpClient.requestCredentialResponse(
        endpoint = request.credentialEndpoint.toString(),
        accessToken = request.accessToken,
        prepared = prepared,
        dpop = dpop,
        onStage = onStage,
    )
    return CredentialEndpointExchange(response, prepared.responseDecryptionKey)
}

internal suspend fun HttpClient.requestCredentialResponse(
    endpoint: String,
    accessToken: String,
    prepared: PreparedCredentialRequest,
    dpop: DpopRequestContext?,
    expectedTransactionId: String? = null,
    onStage: (CredentialIssuanceStage) -> Unit = {},
): CredentialResponse {
    val scheme = if (dpop != null) "DPoP" else "Bearer"
    var dpopNonce: String? = dpop?.nonce

    repeat(DPOP_NONCE_ATTEMPTS) { attempt ->
        val proof = dpop?.let {
            onStage(CredentialIssuanceStage.PROOF)
            buildDpopProof(
                keyMaterial = it.keyMaterial,
                algorithms = it.algorithms,
                endpoint = endpoint,
                accessToken = accessToken,
                nonce = dpopNonce,
            )
        }
        onStage(CredentialIssuanceStage.REQUEST)
        val response = postFollowingRedirects(endpoint) {
            header(HttpHeaders.Authorization, "$scheme ${accessToken}")
            proof?.let { header(DPOP_HEADER, it) }
            contentType(prepared.contentType)
            if (prepared.contentType.isApplicationJwt()) acceptCredentialResponse()
            setBody(prepared.body)
        }
        dpop?.nonce = response.headers[DPOP_NONCE_HEADER] ?: dpopNonce
        if (response.status.isSuccess()) {
            onStage(CredentialIssuanceStage.RESPONSE)
            val credentialResponse = response.readCredentialResponse(prepared.responseDecryptionKey)
            return credentialResponse.validateCredentialResponse(response.status.value, expectedTransactionId)
        }

        // Read the DPoP signals before the body: oauthErrorCode() consumes it.
        val suppliedNonce = response.headers[DPOP_NONCE_HEADER]
        val oauthError = response.oauthErrorCode()
        if (
            attempt == 0 &&
            proof != null &&
            oauthError == USE_DPOP_NONCE &&
            !suppliedNonce.isNullOrBlank()
        ) {
            log.debug { "Credential endpoint demanded a DPoP nonce; retrying once with it" }
            dpopNonce = suppliedNonce
            return@repeat
        }

        // Retain draft-15 §9.3 pending responses accepted by the released polling API.
        if (expectedTransactionId != null && response.status == HttpStatusCode.BadRequest && oauthError == "issuance_pending") {
            onStage(CredentialIssuanceStage.RESPONSE)
            return endpointJson.decodeFromString<CredentialResponse>(response.bodyAsText())
                .validateLegacyDeferredPendingResponse(expectedTransactionId)
        }

        // try-catch rather than runCatching: the body read suspends, and runCatching would
        // swallow CancellationException.
        val credentialError = try {
            endpointJson.decodeFromString<CredentialError>(response.bodyAsText())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        throw CredentialEndpointException(
            statusCode = response.status.value,
            credentialError = credentialError,
        )
    }
    error("DPoP nonce retry exhausted for the credential endpoint")
}

internal suspend fun HttpResponse.readCredentialResponse(decryptionKey: Crypto2Key?): CredentialResponse {
    if (decryptionKey == null) return body()
    require(contentType().isApplicationJwt()) { "Encrypted credential response must use application/jwt" }
    return endpointJson.decodeFromString(CredentialEncryption.decryptResponse(bodyAsText(), decryptionKey))
}

internal suspend fun requestProofNonce(
    httpClient: HttpClient,
    nonceEndpoint: String?,
): String? = nonceEndpoint?.let {
    NonceRequestBuilder(httpClient).requestNonce(it).cNonce
}

// Draft-15 §9.3: preserve the released polling contract, including its default interval.
internal fun CredentialResponse.validateLegacyDeferredPendingResponse(transactionId: String): CredentialResponse =
    copy(transactionId = this.transactionId ?: transactionId, interval = interval ?: 5)
        .validateCredentialResponse(HttpStatusCode.Accepted.value, transactionId)

/** Existing redirect handling for credential endpoint POSTs; nonce requests never use this path. */
private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

private val endpointJson = Json { ignoreUnknownKeys = true }

private val log = KotlinLogging.logger {}

private suspend fun HttpClient.postFollowingRedirects(
    url: String,
    block: HttpRequestBuilder.() -> Unit
): HttpResponse {
    var response = post(url, block)
    if (response.status.value in REDIRECT_STATUS_CODES) {
        val location = response.headers[HttpHeaders.Location]
        if (location != null) {
            log.debug { "Following redirect to: $location" }
            check(isSameOrigin(url, location)) {
                "Cross-origin redirect from $url to $location is not supported for wallet POST requests"
            }
            response = post(location, block)
        }
    }
    return response
}

private fun isSameOrigin(source: String, target: String): Boolean {
    val sourceUrl = Url(source)
    val targetUrl = Url(target)
    return sourceUrl.protocol == targetUrl.protocol &&
            sourceUrl.host == targetUrl.host &&
            sourceUrl.port == targetUrl.port
}

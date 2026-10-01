package id.waltid.openid4vci.wallet.dpop

import id.waltid.openid4vci.wallet.token.DPoPProofFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

/**
 * POSTs to a DPoP- or Bearer-protected resource and retries once when the server supplies a nonce.
 */
suspend fun HttpClient.postWithDpopNonceRetry(
    endpoint: String,
    accessToken: String,
    authorizationScheme: String,
    dpopProofFactory: DPoPProofFactory?,
    configure: HttpRequestBuilder.() -> Unit,
): HttpResponse {
    var dpopNonce: String? = null
    repeat(DPOP_NONCE_ATTEMPTS) { attempt ->
        val dpopProof = dpopProofFactory?.invoke(endpoint, dpopNonce)
        val response = post(endpoint) {
            header(HttpHeaders.Authorization, "$authorizationScheme $accessToken")
            dpopProof?.let { header(DPOP_HEADER, it) }
            configure()
        }
        val suppliedNonce = response.headers[DPOP_NONCE_HEADER]
        val retryWithNonce = attempt == 0 &&
            dpopProof != null &&
            response.status.value == 401 &&
            !suppliedNonce.isNullOrBlank() &&
            response.headers[HttpHeaders.WWWAuthenticate]?.contains(USE_DPOP_NONCE, ignoreCase = true) == true
        if (retryWithNonce) {
            dpopNonce = suppliedNonce
        } else {
            return response
        }
    }
    error("DPoP nonce retry exhausted for $endpoint")
}

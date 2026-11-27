package id.walt.wallet2.handlers

import id.walt.crypto2.keys.Key
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.wallet2.handlers.WalletIssuanceSessionService.DeferredRequest
import id.waltid.openid4vci.wallet.credential.PreparedCredentialRequest
import id.waltid.openid4vci.wallet.credential.acceptCredentialResponse
import id.waltid.openid4vci.wallet.credential.isApplicationJwt
import id.waltid.openid4vci.wallet.dpop.DPOP_HEADER
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_ATTEMPTS
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import id.waltid.openid4vci.wallet.dpop.USE_DPOP_NONCE
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

internal class DeferredCredentialResponse(
    private val response: HttpResponse,
    private val decryptionKey: Key?,
    val dpopNonce: String?,
    val oauthError: String?,
) {
    val status: HttpStatusCode get() = response.status

    suspend fun readCredential(legacyPending: Boolean = false): CredentialResponse =
        response.readCredentialResponse(decryptionKey.takeIf { status.isSuccess() && !legacyPending })
}

internal suspend fun HttpClient.pollDeferredCredential(
    request: DeferredRequest,
    prepared: PreparedCredentialRequest,
): DeferredCredentialResponse {
    var nonce = request.dpopNonce
    repeat(DPOP_NONCE_ATTEMPTS) { attempt ->
        val proof = try {
            request.dpop?.let { algorithms ->
                buildDpopProof(
                    keyMaterial = request.keyMaterial,
                    algorithms = algorithms,
                    endpoint = request.endpoint,
                    accessToken = request.accessToken,
                    nonce = nonce,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IssuanceStageException(WalletIssuanceErrorCode.CRYPTO, error)
        }
        val response = try {
            post(request.endpoint) {
                header(HttpHeaders.Authorization, "${authorizationScheme(request.tokenType)} ${request.accessToken}")
                proof?.let { header(DPOP_HEADER, it) }
                if (prepared.contentType.isApplicationJwt()) acceptCredentialResponse()
                contentType(prepared.contentType)
                setBody(prepared.body)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IssuanceStageException(WalletIssuanceErrorCode.NETWORK, error)
        }
        val oauthError = response.oauthErrorCode()
        val suppliedNonce = response.headers[DPOP_NONCE_HEADER]
        if (
            attempt == 0 &&
            proof != null &&
            oauthError == USE_DPOP_NONCE &&
            !suppliedNonce.isNullOrBlank()
        ) {
            nonce = suppliedNonce
            return@repeat
        }
        return DeferredCredentialResponse(response, prepared.responseDecryptionKey, suppliedNonce ?: nonce, oauthError)
    }
    error("DPoP nonce retry exhausted")
}

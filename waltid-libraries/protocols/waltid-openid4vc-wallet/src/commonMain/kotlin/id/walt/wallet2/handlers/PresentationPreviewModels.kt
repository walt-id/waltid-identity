@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.consent.*
import id.waltid.openid4vp.wallet.PresentationRequestError
import id.waltid.openid4vp.wallet.ResolvedDcApiRequest
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request for a consent preview of an OpenID4VP authorization request.
 *
 * [key] (inline) takes precedence over [keyId] for wallet capability advertisement and format
 * validation during preview. Defaults to the wallet's default signing key.
 */
@Serializable
data class PreviewPresentationRequest(
    val requestUrl: Url,
    /** Inline signing key; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
)

sealed interface PreviewPresentationResult {
    val handle: PresentationPreviewHandle
    val resolvedAuthorizationRequest: ResolvedAuthorizationRequest
    val authorizationRequest: AuthorizationRequest
        get() = resolvedAuthorizationRequest.authorizationRequest

    data class Ready(
        override val handle: PresentationPreviewHandle,
        override val resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
        /** Response-encryption selection derived from this authenticated request, or `null` for a plain response. */
        val responseEncryption: ResponseEncryption.Metadata?,
        val credentialOptions: List<PresentationCredentialOption>,
        val credentialRequirements: List<PresentationCredentialRequirement>,
        val transactionData: List<PresentationTransactionDataItem>,
    ) : PreviewPresentationResult

    data class Invalid(
        override val handle: PresentationPreviewHandle,
        override val resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
        val error: PresentationRequestError,
    ) : PreviewPresentationResult
}

/**
 * Stateless consent-preview result for HTTP APIs that must not retain a server-side preview session.
 *
 * Same UI payload as [PreviewPresentationResult], but without a [PresentationPreviewHandle].
 * [authorizationRequest] is returned for display / technical details only — callers must **not**
 * echo it into [buildVpToken] or [sendAuthorizationResponse]. Complete the flow by passing the
 * original `requestUrl` to those steps (or [rejectPresentationByRequestUrl]).
 */
sealed interface StatelessPreviewPresentationResult {
    val authorizationRequest: AuthorizationRequest

    data class Ready(
        override val authorizationRequest: AuthorizationRequest,
        val keyId: String,
        val responseEncryption: ResponseEncryption.Metadata?,
        val credentialOptions: List<PresentationCredentialOption>,
        val credentialRequirements: List<PresentationCredentialRequirement>,
        val transactionData: List<PresentationTransactionDataItem>,
    ) : StatelessPreviewPresentationResult

    data class Invalid(
        override val authorizationRequest: AuthorizationRequest,
        val error: PresentationRequestError,
    ) : StatelessPreviewPresentationResult
}

data class PreviewDcApiPresentationRequest(
    val protocol: String,
    val data: JsonObject,
    val origin: String,
    val eligibleCredentialIds: Set<String>? = null,
)

data class PreviewDcApiPresentationResult(
    val requestId: String,
    val resolvedRequest: ResolvedDcApiRequest,
    val credentialOptions: List<PresentationCredentialOption>,
    val credentialRequirements: List<PresentationCredentialRequirement>,
    val transactionData: List<PresentationTransactionDataItem>,
)

@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.consent.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class SubmitPresentationRequest(
    val previewHandle: PresentationPreviewHandle,
    val selectedCredentialOptions: List<PresentationCredentialSelection>,
    val selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
    val keyId: String? = null,
    val did: String? = null,
    val runPolicies: Boolean? = null,
    val paymentConsentRevision: String? = null,
)

@Serializable
data class RejectPresentationRequest(
    val previewHandle: PresentationPreviewHandle,
    val errorCode: String? = null,
    val errorDescription: String? = null,
)

/** Reject a presentation by re-resolving the original request URL (no preview handle / session). */
@Serializable
data class RejectPresentationByRequestUrlRequest(
    val requestUrl: Url,
    val errorCode: String? = null,
    val errorDescription: String? = null,
)

data class SubmitDcApiPresentationRequest(
    val requestId: String,
    val selectedCredentialOptions: List<PresentationCredentialSelection>,
    val selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
    val did: String? = null,
    val paymentConsentRevision: String? = null,
)

class MissingPresentationPreviewException :
    IllegalStateException("Presentation request preview expired or was not found; preview the request again before responding.")

class InvalidPresentationTransactionDataException(val reason: String) :
    IllegalStateException("Presentation request is invalid (invalid_transaction_data): $reason")

/**
 * Builds a VP token after re-resolving [requestUrl].
 *
 * [requestUrl] is re-resolved and revalidated server-side so DCQL / nonce / transaction
 * data are never taken from a client-echoed [AuthorizationRequest].
 *
 * Prefer [selectedCredentialOptions] (+ optional [selectedDisclosureOptions]) for consent UIs.
 * [selectedCredentialIds] remains for the legacy resolve → match → build path.
 *
 * After a stateless preview, pass the Ready `keyId` here so build uses the same signing key
 * that preview validated against.
 */
@Serializable
data class BuildVpTokenRequest(
    /**
     * Original OpenID4VP request URL from preview / resolve-request.
     * Re-resolved on every call; do not echo the preview [AuthorizationRequest].
     */
    override val requestUrl: Url,
    /**
     * User-selected credential options (queryId + credentialId), preferred for consent UIs.
     * When non-empty, takes precedence over [selectedCredentialIds].
     */
    val selectedCredentialOptions: List<PresentationCredentialSelection> = emptyList(),
    /** Optional selectively disclosable claim paths for the selected credentials. */
    val selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
    /**
     * Legacy credential IDs grouped by DCQL query ID (from match-credentials-from-store).
     * Used when [selectedCredentialOptions] is empty.
     */
    val selectedCredentialIds: Map<String, List<String>> = emptyMap(),
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    /** DID to use as holder binding. Defaults to the wallet's default DID. */
    val did: String? = null,
) : VpRequestSource

@Serializable
data class BuildVpTokenResult(
    /** The serialized `vp_token` JSON string, ready for [SendAuthorizationResponseRequest]. */
    val vpToken: String,
    /** The Self-Issued ID Token for SIOPv2 flows, or null for plain vp_token flows. */
    val idToken: String? = null,
)

/**
 * Request to send the authorization response to the verifier.
 *
 * Final step of the manual presentation flow. [requestUrl] is re-resolved and revalidated
 * so response destination / mode / encryption come from the verifier, not the client.
 */
@Serializable
data class SendAuthorizationResponseRequest(
    /**
     * Original OpenID4VP request URL from preview.
     * Re-resolved on every call; do not echo the preview [AuthorizationRequest].
     */
    override val requestUrl: Url,
    /** The VP token from [buildVpToken]. */
    val vpToken: String,
    /** The ID token from [buildVpToken], or null. */
    val idToken: String? = null,
) : VpRequestSource

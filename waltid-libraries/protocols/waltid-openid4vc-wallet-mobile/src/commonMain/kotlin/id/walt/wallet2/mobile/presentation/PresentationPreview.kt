package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.mobile.Wallet

/**
 * Preview of an OpenID4VP presentation request before the wallet submits a VP token.
 *
 * @property previewHandle Opaque handle required for submit, reject, or local dismissal.
 * @property request Verifier, protocol, and transaction metadata extracted from the request.
 * @property credentialOptions Wallet-local credentials that can satisfy the presentation request.
 * @property credentialRequirements Required DCQL credential query combinations that must be satisfied before submission.
 */
public data class PresentationPreview(
    val previewHandle: PresentationPreviewHandle,
    val request: PresentationRequest,
    val credentialOptions: List<PresentationCredentialOption>,
    val credentialRequirements: List<PresentationCredentialRequirement> = emptyList(),
)

/** Result of resolving and validating an OpenID4VP request for presentation preview. */
public sealed interface PresentationPreviewResult {
    /** The request is valid and can be reviewed, submitted, or declined. */
    public data class Ready(
        /** Presentation request metadata and matching wallet credentials available for review. */
        public val preview: PresentationPreview,
    ) : PresentationPreviewResult

    /** The request cannot be fulfilled, but the detected protocol error can be returned after user interaction. */
    public data class Invalid(
        /** Opaque handle required to reject or discard this reviewed request. */
        public val previewHandle: PresentationPreviewHandle,
        /** Validated response destination and request context to show before returning the error. */
        public val request: PartialPresentationRequest,
        /** Standard OpenID4VP error detected by the wallet. */
        public val errorCode: PresentationErrorCode,
        /** Local diagnostic intended for wallet UI; it is not sent to the verifier automatically. */
        public val message: String,
    ) : PresentationPreviewResult
}

/**
 * Opaque presentation preview handle. It is valid only for the wallet that created it.
 *
 * @property value Opaque identifier returned by [Wallet.previewPresentation].
 */
public data class PresentationPreviewHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Presentation preview handle must not be blank" }
    }

    /** Returns a redacted representation that does not reveal [value]. */
    public override fun toString(): String = "PresentationPreviewHandle(<redacted>)"
}

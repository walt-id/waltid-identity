package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.mobile.MobileWallet

/**
 * Preview of an OpenID4VP presentation request before the wallet submits a VP token.
 *
 * @property previewHandle Opaque handle required for submit, reject, or local dismissal.
 * @property request Verifier, protocol, and transaction metadata extracted from the request.
 * @property credentialOptions Wallet-local credentials that can satisfy the presentation request.
 * @property credentialRequirements Required DCQL credential query combinations that must be satisfied before submission.
 */
public data class MobileWalletPresentationPreview(
    val previewHandle: MobileWalletPresentationPreviewHandle,
    val request: MobileWalletPresentationRequestInfo,
    val credentialOptions: List<MobileWalletPresentationCredentialOption>,
    val credentialRequirements: List<MobileWalletPresentationCredentialRequirement> = emptyList(),
)

/** Result of resolving and validating an OpenID4VP request for presentation preview. */
public sealed interface MobileWalletPresentationPreviewResult {
    /** The request is valid and can be reviewed, submitted, or declined. */
    public data class Ready(
        /** Presentation request metadata and matching wallet credentials available for review. */
        public val preview: MobileWalletPresentationPreview,
    ) : MobileWalletPresentationPreviewResult

    /** The request cannot be fulfilled, but the detected protocol error can be returned after user interaction. */
    public data class Invalid(
        /** Opaque handle required to reject or discard this reviewed request. */
        public val previewHandle: MobileWalletPresentationPreviewHandle,
        /** Validated response destination and request context to show before returning the error. */
        public val request: MobileWalletPresentationRequestContext,
        /** Standard OpenID4VP error detected by the wallet. */
        public val errorCode: MobileWalletPresentationErrorCode,
        /** Local diagnostic intended for wallet UI; it is not sent to the verifier automatically. */
        public val message: String,
    ) : MobileWalletPresentationPreviewResult
}

/**
 * Opaque presentation preview handle. It is valid only for the wallet that created it.
 *
 * @property value Opaque identifier returned by [MobileWallet.previewPresentation].
 */
public data class MobileWalletPresentationPreviewHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Presentation preview handle must not be blank" }
    }

    /** Returns a redacted representation that does not reveal [value]. */
    public override fun toString(): String = "MobileWalletPresentationPreviewHandle(<redacted>)"
}

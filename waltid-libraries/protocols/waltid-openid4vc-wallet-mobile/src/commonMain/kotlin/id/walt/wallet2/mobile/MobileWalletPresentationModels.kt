package id.walt.wallet2.mobile

/**
 * Preview of an OpenID4VP presentation request before the wallet submits a VP token.
 *
 * @property request Verifier, protocol, and transaction metadata extracted from the request.
 * @property credentialOptions Wallet-local credentials that can satisfy the presentation request.
 * @property credentialRequirements Required DCQL credential query combinations that must be satisfied before submission.
 * @property encryption Authenticated response-encryption requirements for the retained request.
 */
public data class MobileWalletPresentationPreview(
    public val request: MobileWalletPresentationRequestInfo,
    public val credentialOptions: List<MobileWalletPresentationCredentialOption>,
    public val credentialRequirements: List<MobileWalletPresentationCredentialRequirement> = emptyList(),
    public val encryption: MobileWalletEncryptionInfo = MobileWalletEncryptionInfo.NotRequired,
)

/** Authenticated encryption requirements for an OpenID4VP response (OID4VP 1.0 §8.3). */
public sealed interface MobileWalletEncryptionInfo {
    /** Whether response encryption is required. */
    public val isRequired: Boolean
    /** Negotiated JWE content-encryption algorithm, when required. */
    public val contentEncryptionAlgorithm: String?
    /** Negotiated JWE key-management algorithm, when required. */
    public val keyManagementAlgorithm: String?
    /** RFC 7638 thumbprint of the verifier encryption key, when required. */
    public val verifierKeyThumbprint: String?

    /** The verifier requested a cleartext response mode. */
    public data object NotRequired : MobileWalletEncryptionInfo {
        override val isRequired: Boolean = false
        override val contentEncryptionAlgorithm: String? = null
        override val keyManagementAlgorithm: String? = null
        override val verifierKeyThumbprint: String? = null
    }

    /** The verifier requested an encrypted response with a fully negotiated JWE context. */
    public data class Required(
        override val contentEncryptionAlgorithm: String,
        override val keyManagementAlgorithm: String,
        override val verifierKeyThumbprint: String,
    ) : MobileWalletEncryptionInfo {
        override val isRequired: Boolean = true
    }
}

/** Result of resolving and validating an OpenID4VP request for presentation preview. */
public sealed interface MobileWalletPresentationPreviewResult {
    /** The request is valid and can be reviewed, submitted, or declined. */
    public data class Ready(
        /** Presentation request metadata and matching wallet credentials available for review. */
        public val preview: MobileWalletPresentationPreview,
    ) : MobileWalletPresentationPreviewResult

    /** The request cannot be fulfilled, but the detected protocol error can be returned after user interaction. */
    public data class Invalid(
        /** Validated response destination and request context to show before returning the error. */
        public val request: MobileWalletPresentationRequestInfo,
        /** Standard OpenID4VP error detected by the wallet. */
        public val errorCode: MobileWalletPresentationErrorCode,
        /** Local diagnostic intended for wallet UI; it is not sent to the verifier automatically. */
        public val message: String,
    ) : MobileWalletPresentationPreviewResult
}

/**
 * A required presentation credential-query combination.
 *
 * Each inner option contains DCQL credential query IDs that must all be selected together.
 * At least one option must be satisfied for this requirement to be complete.
 *
 * @property options Alternative DCQL credential query ID sets that satisfy this requirement.
 */
public data class MobileWalletPresentationCredentialRequirement(
    val options: List<List<String>>,
)

/**
 * Verifier and transaction metadata extracted from a presentation request.
 *
 * @property clientId Raw OpenID4VP `client_id` value, when available.
 * @property verifierName Human-readable verifier name derived from request metadata or the client identifier.
 * @property responseUri Verifier response URI to which the wallet will submit the presentation, when provided.
 * @property state OpenID4VP state value supplied by the verifier, when provided.
 * @property nonce OpenID4VP nonce value supplied by the verifier, when provided.
 * @property transactionData Decoded transaction data items requested by the verifier.
 * @property responseMode Serialized OpenID4VP response mode requested by the verifier.
 */
public data class MobileWalletPresentationRequestInfo(
    val clientId: String?,
    val verifierName: String?,
    val responseUri: String?,
    val state: String?,
    val nonce: String?,
    val transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
    val responseMode: String? = null,
)

/**
 * A wallet credential that satisfies one DCQL credential query in the presentation request.
 *
 * @property queryId DCQL credential query identifier this credential can satisfy.
 * @property credentialId Wallet-local credential identifier to submit when this option is selected.
 * @property multiple Whether the DCQL credential query allows sharing multiple matching credentials.
 * @property format Credential format, such as `jwt_vc_json`, `vc+sd-jwt`, or `mso_mdoc`.
 * @property issuer Issuer identifier extracted from the credential when available.
 * @property subject Subject identifier extracted from the credential when available.
 * @property label Optional display label stored with the credential.
 * @property credentialDataJson Parsed credential data encoded as JSON for app-side display.
 * @property disclosures Claim values requested by the verifier for this credential option.
 */
public data class MobileWalletPresentationCredentialOption(
    val queryId: String,
    val credentialId: String,
    val multiple: Boolean = false,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val credentialDataJson: String,
    val disclosures: List<MobileWalletPresentationDisclosure> = emptyList(),
)

/**
 * User-selected presentation credential option.
 *
 * @property queryId DCQL credential query identifier from [MobileWalletPresentationCredentialOption.queryId].
 * @property credentialId Wallet-local credential identifier from [MobileWalletPresentationCredentialOption.credentialId].
 */
public data class MobileWalletPresentationCredentialSelection(
    val queryId: String,
    val credentialId: String,
)

/**
 * User-selected selectively disclosable claim for a selected presentation credential option.
 *
 * Apps should treat [path] as an opaque token returned by [MobileWalletPresentationDisclosure.path]
 * for the same preview. The wallet validates the token against the resolved presentation request
 * before submitting.
 *
 * @property queryId DCQL credential query identifier from [MobileWalletPresentationCredentialOption.queryId].
 * @property credentialId Wallet-local credential identifier from [MobileWalletPresentationCredentialOption.credentialId].
 * @property path Opaque disclosure path token from [MobileWalletPresentationDisclosure.path].
 */
public data class MobileWalletPresentationDisclosureSelection(
    val queryId: String,
    val credentialId: String,
    val path: String,
)

/**
 * Claim or selective-disclosure value that may be shared for a matched credential.
 *
 * @property path Opaque claim path token supplied by the presentation engine.
 * @property name Optional human-readable claim name supplied by the presentation engine.
 * @property valueJson Claim value encoded as JSON.
 * @property displayValue Optional presentation-engine display value for the claim.
 * @property selectivelyDisclosable `true` when the credential format can selectively disclose this claim.
 * @property required `true` when the presentation request requires this claim for the matched query.
 * @property selectable `true` when apps may let the user toggle this claim for submission.
 */
public data class MobileWalletPresentationDisclosure(
    val path: String,
    val name: String?,
    val valueJson: String,
    val displayValue: String?,
    val selectivelyDisclosable: Boolean,
    val required: Boolean = !selectivelyDisclosable,
    val selectable: Boolean = selectivelyDisclosable && !required,
)

/**
 * Decoded transaction_data item attached to a presentation request.
 *
 * @property type Transaction data type value.
 * @property displayName Human-readable label from the accepted mobile transaction data profile.
 * @property credentialQueryIds Credential query identifiers to which this transaction data applies.
 * @property supportedFields Profile-declared transaction-data fields this wallet accepts for [type].
 * @property rawJson Full transaction data item encoded as JSON.
 * @property detailsJson Decoded transaction data details encoded as JSON.
 */
public data class MobileWalletTransactionDataItem(
    val type: String,
    val displayName: String,
    val credentialQueryIds: List<String>,
    val supportedFields: List<String>,
    val rawJson: String,
    val detailsJson: String,
)

/**
 * OAuth 2.0 and OpenID4VP 1.0 authorization error codes supported by the wallet.
 *
 * Apps should use [accessDenied] when the user declines, the wallet has no requested credential,
 * or user authentication fails. The remaining values describe protocol or availability failures
 * and should not be presented as end-user choices.
 *
 * @property errorCode Error code returned to the verifier.
 */
public enum class MobileWalletPresentationErrorCode(
    public val errorCode: String,
) {
    accessDenied("access_denied"),
    invalidRequest("invalid_request"),
    invalidClient("invalid_client"),
    invalidScope("invalid_scope"),
    unauthorizedClient("unauthorized_client"),
    unsupportedResponseType("unsupported_response_type"),
    serverError("server_error"),
    temporarilyUnavailable("temporarily_unavailable"),
    vpFormatsNotSupported("vp_formats_not_supported"),
    invalidRequestUriMethod("invalid_request_uri_method"),
    invalidTransactionData("invalid_transaction_data"),
    walletUnavailable("wallet_unavailable"),
}

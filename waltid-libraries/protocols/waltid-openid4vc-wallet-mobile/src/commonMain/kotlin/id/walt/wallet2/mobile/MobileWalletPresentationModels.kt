package id.walt.wallet2.mobile

/**
 * Preview of an OpenID4VP presentation request before the wallet submits a VP token.
 *
 * @property request Verifier and protocol metadata extracted from the request.
 * @property credentialOptions Wallet-local credentials that can satisfy the presentation request.
 */
public data class MobileWalletPresentationPreview(
    val request: MobileWalletPresentationRequestInfo,
    val credentialOptions: List<MobileWalletPresentationCredentialOption>,
)

/**
 * Verifier metadata extracted from a presentation request.
 *
 * @property clientId Raw OpenID4VP `client_id` value, when available.
 * @property verifierName Human-readable verifier name derived from request metadata or the client identifier.
 * @property responseUri Verifier response URI to which the wallet will submit the presentation, when provided.
 * @property state OpenID4VP state value supplied by the verifier, when provided.
 * @property nonce OpenID4VP nonce value supplied by the verifier, when provided.
 */
public data class MobileWalletPresentationRequestInfo(
    val clientId: String?,
    val verifierName: String?,
    val responseUri: String?,
    val state: String?,
    val nonce: String?,
)

/**
 * A wallet credential that satisfies one DCQL credential query in the presentation request.
 *
 * @property queryId DCQL credential query identifier this credential can satisfy.
 * @property credentialId Wallet-local credential identifier to submit when this option is selected.
 * @property format Credential format, such as `jwt_vc_json`, `vc+sd-jwt`, or `mso_mdoc`.
 * @property issuer Issuer identifier extracted from the credential when available.
 * @property subject Subject identifier extracted from the credential when available.
 * @property label Optional display label stored with the credential.
 * @property credentialDataJson Parsed credential data encoded as JSON for app-side display, when available.
 * @property disclosures Claim values requested by the verifier for this credential option.
 */
public data class MobileWalletPresentationCredentialOption(
    val queryId: String,
    val credentialId: String,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val credentialDataJson: String?,
    val disclosures: List<MobileWalletPresentationDisclosure> = emptyList(),
)

/**
 * Claim or selective-disclosure value that may be shared for a matched credential.
 *
 * @property path JSONPath-like claim path supplied by the presentation engine.
 * @property name Optional human-readable claim name supplied by the presentation engine.
 * @property valueJson Claim value encoded as JSON.
 * @property displayValue Optional presentation-engine display value for the claim.
 * @property selectivelyDisclosable `true` when the claim can be selectively disclosed.
 */
public data class MobileWalletPresentationDisclosure(
    val path: String,
    val name: String?,
    val valueJson: String,
    val displayValue: String?,
    val selectivelyDisclosable: Boolean,
)

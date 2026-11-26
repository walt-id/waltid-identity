package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection

/**
 * The exact credential and claim choices used for review, payment consent and submission.
 * @param credentials Credential options selected by the holder.
 * @param disclosures Optional claims to disclose. Null uses the request-matched defaults.
 * An explicit empty list selects no optional claims.
 */
public class MobileWalletPresentationSelection(
    credentials: List<MobileWalletPresentationCredentialSelection>,
    disclosures: List<MobileWalletPresentationDisclosureSelection>? = null,
) {
    /** Selected credentials captured at construction. */
    public val credentials: List<MobileWalletPresentationCredentialSelection> = buildList { addAll(credentials) }

    /** Selected disclosures captured at construction, or null for the request-matched defaults. */
    public val disclosures: List<MobileWalletPresentationDisclosureSelection>? = disclosures?.let { buildList { addAll(it) } }

    internal fun coreCredentials(): List<PresentationCredentialSelection> =
        credentials.map { PresentationCredentialSelection(it.queryId, it.credentialId) }

    internal fun coreDisclosures(): List<PresentationDisclosureSelection>? =
        disclosures?.map { PresentationDisclosureSelection(it.queryId, it.credentialId, it.path) }
}

/**
 * A wallet credential that satisfies one DCQL credential query in the presentation request.
 *
 * @property queryId DCQL credential query identifier this credential can satisfy.
 * @property credentialId Wallet-local credential identifier to submit when this option is selected.
 * @property format Credential format, such as `jwt_vc_json`, `vc+sd-jwt`, or `mso_mdoc`.
 * @property credentialDataJson Parsed credential data encoded as JSON for app-side display.
 * @property multiple Whether the DCQL credential query allows sharing multiple matching credentials.
 * @property issuer Issuer identifier extracted from the credential when available.
 * @property subject Subject identifier extracted from the credential when available.
 * @property label Optional display label stored with the credential.
 * @property disclosures Claim values requested by the verifier for this credential option.
 * @property metadataJson Optional stored display metadata encoded as JSON, including issuer and
 *   credential card art.
 */
public data class MobileWalletPresentationCredentialOption(
    val queryId: String,
    val credentialId: String,
    val format: String,
    val credentialDataJson: String,
    val multiple: Boolean = false,
    val issuer: String? = null,
    val subject: String? = null,
    val label: String? = null,
    val disclosures: List<MobileWalletPresentationDisclosure> = emptyList(),
    val metadataJson: String? = null,
) {
    /** Identifies this exact credential in its query when the holder selects it. */
    public val selection: MobileWalletPresentationCredentialSelection
        get() = MobileWalletPresentationCredentialSelection(queryId, credentialId)

    init {
        require(queryId.isNotBlank()) {
            "A presentation credential option query ID must not be blank."
        }
    }
}

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
 * @property requested `false` for additional clear-text data the credential must transmit.
 */
public data class MobileWalletPresentationDisclosure(
    val path: String,
    val name: String?,
    val valueJson: String,
    val displayValue: String?,
    val selectivelyDisclosable: Boolean,
    val required: Boolean = !selectivelyDisclosable,
    val selectable: Boolean = selectivelyDisclosable && !required,
    val requested: Boolean = true,
) {
    init {
        require(requested || (!selectivelyDisclosable && !required && !selectable)) {
            "Additional shared data must be immutable and independent of request requirements."
        }
        require(!selectable || (selectivelyDisclosable && !required)) {
            "A selectable disclosure must be selectively disclosable and optional."
        }
    }
}

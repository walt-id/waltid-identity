package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection
import id.walt.wallet2.mobile.credentials.MobileWalletCredential

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
@ConsistentCopyVisibility
public data class MobileWalletPresentationCredentialOption internal constructor(
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
    /** Creates an option from a wallet credential and the review facts for this query. */
    public constructor(
        queryId: String,
        credential: MobileWalletCredential,
        multiple: Boolean = false,
        disclosures: List<MobileWalletPresentationDisclosure> = emptyList(),
    ) : this(queryId, credential.id, credential.format, credential.credentialDataJson, multiple,
        credential.issuer, credential.subject, credential.label, buildList { addAll(disclosures) }, credential.metadataJson)

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

/** How the matched request and credential determine inclusion of this claim. */
public enum class DisclosureDisposition {
    /** Unrequested cleartext included whenever the credential is presented. */
    AdditionalCleartext,
    /** A required requested claim carried in cleartext. */
    RequiredCleartext,
    /** An optional requested claim carried in cleartext. */
    OptionalCleartext,
    /** A required claim in a selectively disclosable format. */
    RequiredSelective,
    /** An optional selectively disclosable claim without an independent holder choice. */
    FixedSelective,
    /** An optional claim the holder can independently include or omit. */
    Selectable;

    /** The credential supports selective disclosure for this claim, even if the request fixes it. */
    public val selectivelyDisclosable: Boolean get() = this == RequiredSelective || this == FixedSelective || this == Selectable
    /** Presenting the matched request includes this claim as required. */
    public val required: Boolean get() = this == RequiredCleartext || this == RequiredSelective
    /** The holder can independently toggle the claim for this request. */
    public val selectable: Boolean get() = this == Selectable
    /** The verifier requested this claim. Additional cleartext travels with the credential. */
    public val requested: Boolean get() = this != AdditionalCleartext
}

/** A claim shown to the holder, with the inclusion rules for this particular request. */
public data class MobileWalletPresentationDisclosure(
    val path: String,
    val valueJson: String,
    val disposition: DisclosureDisposition,
    val name: String? = null,
    val displayValue: String? = null,
) {
    public val selectivelyDisclosable: Boolean get() = disposition.selectivelyDisclosable
    public val required: Boolean get() = disposition.required
    public val selectable: Boolean get() = disposition.selectable
    public val requested: Boolean get() = disposition.requested
}

internal fun disclosureDisposition(selective: Boolean, required: Boolean, selectable: Boolean, requested: Boolean): DisclosureDisposition {
    require(requested || (!selective && !required && !selectable))
    require(!selectable || (selective && !required))
    return when {
        !requested -> DisclosureDisposition.AdditionalCleartext
        selectable -> DisclosureDisposition.Selectable
        selective && required -> DisclosureDisposition.RequiredSelective
        selective -> DisclosureDisposition.FixedSelective
        required -> DisclosureDisposition.RequiredCleartext
        else -> DisclosureDisposition.OptionalCleartext
    }
}

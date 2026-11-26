package id.walt.wallet2.mobile.presentation

import id.walt.wallet2.handlers.PresentationCredentialSelection as CorePresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection as CorePresentationDisclosureSelection
import id.walt.wallet2.mobile.credentials.WalletCredential

/**
 * The exact credential and claim choices used for review, payment consent and submission.
 * @param credentials Credential options selected by the holder.
 * @param disclosures Optional claims to disclose. Null uses the request-matched defaults.
 * An explicit empty list selects no optional claims.
 */
public class PresentationSelection(
    credentials: List<PresentationCredentialSelection>,
    disclosures: List<PresentationDisclosureSelection>? = null,
) {
    /** Selected credentials captured at construction. */
    public val credentials: List<PresentationCredentialSelection> = buildList { addAll(credentials) }

    /** Selected disclosures captured at construction, or null for the request-matched defaults. */
    public val disclosures: List<PresentationDisclosureSelection>? = disclosures?.let { buildList { addAll(it) } }

    internal fun coreCredentials(): List<CorePresentationCredentialSelection> =
        credentials.map { CorePresentationCredentialSelection(it.queryId, it.credentialId) }

    internal fun coreDisclosures(): List<CorePresentationDisclosureSelection>? =
        disclosures?.map { CorePresentationDisclosureSelection(it.queryId, it.credentialId, it.path) }
}

/**
 * A wallet credential matching one DCQL query and the disclosures available for that match.
 * @property queryId Matched credential query.
 * @property credential Credential and its stored display metadata.
 * @property multiple Whether the query accepts multiple credentials.
 */
public class PresentationCredentialOption(
    public val queryId: String,
    public val credential: WalletCredential,
    public val multiple: Boolean = false,
    disclosures: List<PresentationDisclosure> = emptyList(),
) {
    /** Claims available for review for this match. */
    public val disclosures: List<PresentationDisclosure> = buildList { addAll(disclosures) }

    /** Identifies this exact credential in its query. */
    public val selection: PresentationCredentialSelection
        get() = PresentationCredentialSelection(queryId, credential.id)

    init { require(queryId.isNotBlank()) { "A presentation credential option query ID must not be blank." } }

    /** Compares the matched credential and the review choices for its query. */
    override fun equals(other: Any?): Boolean = other is PresentationCredentialOption &&
        queryId == other.queryId && credential == other.credential && multiple == other.multiple && disclosures == other.disclosures

    /** Hash of the query, credential and available review choices. */
    override fun hashCode(): Int = 31 * (31 * (31 * queryId.hashCode() + credential.hashCode()) + multiple.hashCode()) + disclosures.hashCode()
}

/**
 * User-selected presentation credential option.
 *
 * @property queryId DCQL credential query identifier from [PresentationCredentialOption.queryId].
 * @property credentialId Wallet-local credential identifier from [PresentationCredentialOption.credential].
 */
public data class PresentationCredentialSelection(
    val queryId: String,
    val credentialId: String,
)

/**
 * User-selected selectively disclosable claim for a selected presentation credential option.
 *
 * Apps should treat [path] as an opaque token returned by [PresentationDisclosure.path]
 * for the same preview. The wallet validates the token against the resolved presentation request
 * before submitting.
 *
 * @property queryId DCQL credential query identifier from [PresentationCredentialOption.queryId].
 * @property credentialId Wallet-local credential identifier from [PresentationCredentialOption.credential].
 * @property path Opaque disclosure path token from [PresentationDisclosure.path].
 */
public data class PresentationDisclosureSelection(
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

/**
 * A claim shown to the holder, with the inclusion rules for this particular request.
 * @property path Wallet-issued claim path used when selecting this disclosure.
 * @property valueJson Claim value encoded as JSON.
 * @property disposition Whether this claim travels with the credential or can be selected independently.
 * @property name Optional human-readable claim name.
 * @property displayValue Optional formatted value for display.
 */
public data class PresentationDisclosure(
    val path: String,
    val valueJson: String,
    val disposition: DisclosureDisposition,
    val name: String? = null,
    val displayValue: String? = null,
) {
    /** Whether the credential supports selective disclosure of this claim. */
    public val selectivelyDisclosable: Boolean get() = disposition.selectivelyDisclosable
    /** Whether satisfying the request requires this claim. */
    public val required: Boolean get() = disposition.required
    /** Whether the holder can independently toggle this claim. */
    public val selectable: Boolean get() = disposition.selectable
    /** Whether the verifier requested this claim. */
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

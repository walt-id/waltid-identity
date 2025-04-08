package id.walt.credentials.signatures.sdjwt

import id.walt.credentials.formats.DigitalCredential

interface SelectivelyDisclosableVerifiableCredential {
    /**
     * Disclosables contained within a credential (`_sd` arrays).
     *
     * Map: Path -> Set of DisclosableString
     *
     * e.g.: `mapOf("$._sd" to setOf("abc"))`
     */
    val disclosables: Map<String, Set<String>>?

    /** Disclosures available to share */
    val disclosures: List<SdJwtSelectiveDisclosure>?

    fun disclose(credential: DigitalCredential, attributes: List<SdJwtSelectiveDisclosure>): String {
        checkNotNull(credential.signed) { "Credential has to be signed to be able to disclose" }
        return "${credential.signed}~${attributes.joinToString("~") { it.encoded() }}"
    }
}

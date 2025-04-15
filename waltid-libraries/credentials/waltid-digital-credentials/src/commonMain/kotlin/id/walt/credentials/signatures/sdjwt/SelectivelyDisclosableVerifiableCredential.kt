package id.walt.credentials.signatures.sdjwt

import id.walt.credentials.formats.DigitalCredential
import kotlinx.serialization.json.JsonObject

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

    val signedWithDisclosures: String?
    val originalCredentialData: JsonObject?

    fun disclose(credential: DigitalCredential, attributes: List<SdJwtSelectiveDisclosure>): String {
        checkNotNull(credential.signed) { "Credential has to be signed to be able to disclose" }
        return "${credential.signed}~${attributes.joinToString("~") { it.asEncoded() }}"
    }

    fun selfCheck() {
        if (disclosures != null) {
            require(disclosables != null) { "Disclosures available, without any disclosables in SD credential?" }
            require(originalCredentialData != null) { "Disclosures available, but no original credential data set?" }
        }
    }
}

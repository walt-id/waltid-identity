@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.transactionlog

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * TS10 section 3.2 presentation detail.
 *
 * Party identifiers refer to registered legal entities, not protocol client IDs or issuer URLs.
 * Null metadata means unknown, not a default assertion (in particular for [isIntermediary]).
 * Empty claim lists are allowed for unsuccessful requests and presentations sharing no claims.
 * Claims describe paths only, never the personal values at those paths.
 */
@Serializable
data class Presentation(
    val listOfClaimsRequested: List<ClaimInfo>,
    val listOfClaimsPresented: List<ClaimInfo>,
    val interactingPartyName: List<String>? = null,
    val privacyPolicy: List<Policy>? = null,
    val transactionalData: JsonObject? = null,
    val reasonOfNoncompletion: String? = null,
    // TODO(TS10 3.2): Populate registration, intermediary and DPA metadata when available.
    val interactingPartyIdentifier: Identifier? = null,
    val interactingPartyContact: List<String>? = null,
    val isIntermediary: Boolean? = null,
    val intermediaryIdentifier: Identifier? = null,
    val intermediaryName: List<String>? = null,
    val intermediaryContact: List<String>? = null,
    val registrarURL: String? = null,
    val purpose: List<MultiLangString>? = null,
    val dpaName: List<String>? = null,
    val dpaCountry: String? = null,
    val dpaContact: List<String>? = null,
    /** All TS10 presentation counterparties act in their service-provider role. */
    @EncodeDefault
    val interactingPartyType: PresentationPartyType = PresentationPartyType.ServiceProvider,
) {
    init {
        requireValidNoncompletionReason(reasonOfNoncompletion)
        require(
            isIntermediary != false ||
                (intermediaryIdentifier == null && intermediaryName == null && intermediaryContact == null)
        ) { "A non-intermediated presentation must not contain intermediary metadata" }
    }
}

/** TS10 section 3.2: the only party type defined for presentation counterparties. */
@Serializable
enum class PresentationPartyType {
    ServiceProvider,
}

/**
 * TS10 section 3.5 issuance detail.
 *
 * [credentialIdentifier] contains issued vct/docType values, not wallet-local credential IDs.
 * It can be empty when issuance failed or was deferred and no credentials have been issued.
 * Issuer role and legal-entity metadata remain unknown unless authoritative information is available.
 */
@Serializable
data class CredentialIssuance(
    val credentialNumberRequested: Int,
    val credentialNumberIssued: Int,
    val credentialIdentifier: List<String>,
    val isUserTriggered: Boolean,
    val interactingPartyName: List<String>? = null,
    // TODO(TS10 3.5): Resolve legal-entity identity, provider role and contact information.
    val interactingPartyIdentifier: Identifier? = null,
    val interactingPartyType: IssuerType? = null,
    val interactingPartyContact: List<String>? = null,
) {
    init {
        require(credentialNumberRequested >= 0 && credentialNumberIssued >= 0) {
            "Credential counts must not be negative"
        }
        require(credentialNumberIssued <= credentialNumberRequested) {
            "Issued credential count must not exceed the requested count"
        }
        require(credentialIdentifier.size <= credentialNumberIssued) {
            "More credential type identifiers than issued credentials"
        }
        require(credentialIdentifier.all(String::isNotBlank)) { "Credential type identifiers must not be blank" }
    }
}

/** TS10 section 3.6 deletion detail; retain the issuer snapshot after the credential is removed. */
@Serializable
data class CredentialDeletion(
    /** The deleted credential's vct/docType, not its wallet-local ID. */
    val credentialIdentifier: String,
    val credentialIssuerName: String? = null,
    // TODO(TS10 3.6): Resolve the issuer's registered legal-entity identifier.
    val credentialIssuerIdentifier: Identifier? = null,
) {
    init {
        require(credentialIdentifier.isNotBlank()) { "Credential type identifier must not be blank" }
    }
}

/** TS10 section 3.15: descriptions of additional operations, not a fallback for unsupported types. */
@Serializable
data class OtherTransaction(
    val description: List<String>,
) {
    init {
        require(description.isNotEmpty() && description.all(String::isNotBlank)) {
            "Other transactions require at least one nonblank description"
        }
    }
}

/** TS10 section 3.3 model only; not currently populated by the SDK. */
@Serializable
data class W2WPresentationRequest(
    val listOfClaimsRequested: List<ClaimInfo>,
    val listOfClaimsPresented: List<ClaimInfo>,
)

/** TS10 section 3.4 model only; not currently populated by the SDK. */
@Serializable
data class W2WPresentation(
    val listOfClaimsRequested: List<ClaimInfo>,
    val listOfClaimsPresented: List<ClaimInfo>,
    val reasonOfNoncompletion: String? = null,
) {
    init {
        requireValidNoncompletionReason(reasonOfNoncompletion)
    }
}

/** TS10 section 3.7 model only; not currently populated by the SDK. */
@Serializable
data class PseudonymGeneration(
    val pseudonym: Pseudonym,
)

/** TS10 section 3.8 model only; not currently populated by the SDK. */
@Serializable
data class PseudonymDeletion(
    val pseudonym: Pseudonym,
)

/** TS10 section 3.9 model only; not currently populated by the SDK. */
@Serializable
data class PseudonymousAuthentication(
    val interactingPartyIdentifier: Identifier,
    val interactingPartyType: PseudonymousAuthenticationPartyType,
    val interactingPartyName: List<String>,
    val pseudonym: Pseudonym,
    val reasonOfNoncompletion: String? = null,
) {
    init {
        requireValidNoncompletionReason(reasonOfNoncompletion)
    }
}

/** TS10 section 3.10 model only; not currently populated by the SDK. */
@Serializable
data class CertificateIssuance(
    val interactingPartyIdentifier: Identifier,
    val interactingPartyType: CertificateIssuerType,
    val interactingPartyName: List<String>,
    val interactingPartyContact: List<String>,
    /** RFC 5280 certificate serial number, not a wallet key ID. */
    val certificateIdentifier: String,
) {
    init {
        require(certificateIdentifier.isNotBlank()) { "Certificate identifier must not be blank" }
    }
}

/** TS10 section 3.11 model only; not currently populated by the SDK. */
@Serializable
data class CertificateDeletion(
    val certificateIdentifier: String,
    val certificateIssuerIdentifier: Identifier,
    val certificateIssuerName: String,
) {
    init {
        require(certificateIdentifier.isNotBlank()) { "Certificate identifier must not be blank" }
    }
}

/** TS10 section 3.12 model only; not currently populated by the SDK. */
@Serializable
data class SigningSealing(
    val interactingPartyIdentifier: Identifier,
    val interactingPartyName: List<String>,
    val interactingPartyContact: List<String>,
    val signingTransactionIdentifier: String,
    val certificateIdentifier: String,
    val dtbsr: String,
    val fileIdentifier: String? = null,
    val fileName: String? = null,
    val fileSize: String? = null,
    @EncodeDefault
    val interactingPartyType: SigningSealingPartyType = SigningSealingPartyType.ESigESealCreationProvider,
) {
    init {
        require(signingTransactionIdentifier.isNotBlank()) { "Signing transaction identifier must not be blank" }
        require(certificateIdentifier.isNotBlank()) { "Certificate identifier must not be blank" }
        require(dtbsr.isNotBlank()) { "Data to be signed representation must not be blank" }
    }
}

/** TS10 section 3.12: the only party type defined for signing and sealing providers. */
@Serializable
enum class SigningSealingPartyType {
    ESigESealCreationProvider,
}

/** TS10 section 3.13 model only; not currently populated by the SDK. */
@Serializable
data class DataDeletionRequest(
    val interactingPartyIdentifier: Identifier,
    val interactingPartyName: List<String>,
    val listOfClaims: List<ClaimInfo>,
) {
    init {
        require(listOfClaims.isNotEmpty()) { "Data deletion request must identify at least one claim" }
    }
}

/** TS10 section 3.14 model only; not currently populated by the SDK. */
@Serializable
data class DPAReport(
    val dpaName: List<String>,
    val dpaCountry: String,
)

/** TS10 section 3.19.3 model only; the SDK does not currently manage these pseudonyms. */
@Serializable
data class Pseudonym(
    val value: String,
    val alias: String? = null,
) {
    init {
        require(value.isNotBlank()) { "Pseudonym value must not be blank" }
    }
}

/** TS10 section 3.9 model only; these roles are not currently used by the SDK. */
@Serializable
enum class PseudonymousAuthenticationPartyType {
    ServiceProvider,
    NaturalPerson,
}

/** TS10 section 3.10 model only; these certificate-provider roles are not currently used by the SDK. */
@Serializable
enum class CertificateIssuerType {
    QCertForESealProvider,
    QCertForESigProvider,
}

/** TS10 section 3.19.2: credential type and claim paths, excluding credential/claim values. */
@Serializable
data class ClaimInfo(
    val credentialIdentifier: String,
    val claims: List<String>,
) {
    init {
        require(credentialIdentifier.isNotBlank()) { "Credential type identifier must not be blank" }
        require(claims.isNotEmpty() && claims.all(String::isNotBlank)) { "Claim paths must not be empty or blank" }
    }
}

/** TS10 section 3.19.9 / TS2 Identifier; [type] is the legal identifier scheme URI. */
@Serializable
data class Identifier(
    val type: String,
    val identifier: String,
)

/** TS10 section 3.19.10 / TS5 MultiLangString; a language-tagged text, not a language-to-text map. */
@Serializable
data class MultiLangString(
    val lang: String,
    val content: String,
)

/** TS10 section 3.19.11 / TS2 Policy. Do not confuse a privacy-policy URI with a privacy statement. */
@Serializable
data class Policy(
    val type: String,
    val policyURI: String,
)

/** TS10 section 3.5 provider roles; do not infer these from a credential format. */
@Serializable
enum class IssuerType {
    QEAAProvider,
    NonQEAAProvider,
    PubEEAProvider,
    PIDProvider,
}

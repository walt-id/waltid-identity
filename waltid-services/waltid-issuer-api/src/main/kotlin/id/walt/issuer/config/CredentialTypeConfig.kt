package id.walt.issuer.config

import id.walt.commons.config.WaltConfig
import id.walt.mdoc.doc.MDocTypes
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialSupported
import id.walt.oid4vc.data.ProofType
import id.walt.oid4vc.data.ProofTypeMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private fun vc(vararg extra: String) = JsonArray(listOf(*extra).map { JsonPrimitive(it) })
private fun vc(credentialSupported: CredentialSupported) = Json.encodeToJsonElement(credentialSupported)

@Serializable
data class CredentialTypeConfig(
    val supportedCredentialTypes: Map<String, JsonElement> = mapOf(
        "BankId" to vc("VerifiableCredential", "BankId"),
        "KycChecksCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycChecksCredential"),
        "KycCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycCredential"),
        "KycDataCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycDataCredential"),
        "PassportCh" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "PassportCh"),
        "PND91Credential" to vc("VerifiableCredential", "PND91Credential"),
        "MortgageEligibility" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "MortgageEligibility"),
        "PortableDocumentA1" to vc("VerifiableCredential", "VerifiableAttestation", "PortableDocumentA1"),
        "OpenBadgeCredential" to vc("VerifiableCredential", "OpenBadgeCredential"),
        "VaccinationCertificate" to vc("VerifiableCredential", "VerifiableAttestation", "VaccinationCertificate"),
        "WalletHolderCredential" to vc("VerifiableCredential", "WalletHolderCredential"),
        "UniversityDegree" to vc("VerifiableCredential", "UniversityDegree"),
        "VerifiableId" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiableId"),
        "CTWalletSameAuthorisedInTime" to vc("VerifiableCredential", "VerifiableAttestation", "CTWalletSameAuthorisedInTime"),
        "CTWalletSameAuthorisedDeferred" to vc("VerifiableCredential", "VerifiableAttestation", "CTWalletSameAuthorisedDeferred"),
        "CTWalletSamePreAuthorisedInTime" to vc("VerifiableCredential", "VerifiableAttestation", "CTWalletSamePreAuthorisedInTime"),
        "CTWalletSamePreAuthorisedDeferred" to vc("VerifiableCredential", "VerifiableAttestation", "CTWalletSamePreAuthorisedDeferred"),
        "AlpsTourReservation" to vc("VerifiableCredential", "VerifiableAttestation", "AlpsTourReservation"),
        "EducationalID" to vc("VerifiableCredential", "VerifiableAttestation", "EducationalID"),
        "HotelReservation" to vc("VerifiableCredential", "VerifiableAttestation", "HotelReservation"),
        "Iso18013DriversLicenseCredential" to vc("VerifiableCredential", "VerifiableAttestation", "Iso18013DriversLicenseCredential"),
        "TaxReceipt" to vc("VerifiableCredential", "VerifiableAttestation", "TaxReceipt"),
        "VerifiablePortableDocumentA1" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiablePortableDocumentA1"),
        "Visa" to vc("VerifiableCredential", "VerifiableAttestation", "Visa"),
        "eID" to vc("VerifiableCredential", "VerifiableAttestation", "eID"),
        "NaturalPersonVerifiableID" to vc("VerifiableCredential", "VerifiableAttestation", "NaturalPersonVerifiableID"),
        "BoardingPass" to vc("VerifiableCredential", "VerifiableAttestation", "BoardingPass"),
        MDocTypes.ISO_MDL to vc(
            CredentialSupported(
                format = CredentialFormat.mso_mdoc,
                cryptographicBindingMethodsSupported = setOf("cose_key"),
                credentialSigningAlgValuesSupported = setOf("ES256"),
                proofTypesSupported = mapOf(ProofType.cwt to ProofTypeMetadata(setOf("ES256"))),
                types = listOf(MDocTypes.ISO_MDL),
                docType = MDocTypes.ISO_MDL
            )
        ),
        "urn:eu.europa.ec.eudi:pid:1" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("jwk"),
                credentialSigningAlgValuesSupported = setOf("ES256"),
                vct = "https://credentials.example.com/eudi_pid_1"
            )
        ),
        "identity_credential_vc+sd-jwt" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("jwk"),
                credentialSigningAlgValuesSupported = setOf("ES256"),
                vct = "https://credentials.example.com/identity_credential"
            )
        )
    ),
) : WaltConfig() {
    fun parse(): Map<String, CredentialSupported> {
        return supportedCredentialTypes.flatMap { entry ->
            when (val element = entry.value) {
                is JsonObject -> {
                    val credentialSupported = Json.decodeFromJsonElement<CredentialSupported>(element.jsonObject)
                    mapOf(entry.key to credentialSupported).entries
                }

                is JsonArray -> {
                    val types = element.jsonArray.map { it.jsonPrimitive.content }

                    CredentialFormat.entries.associate { format ->
                        "${entry.key}_${format.value}" to CredentialSupported(
                            format = format,
                            cryptographicBindingMethodsSupported = setOf("did"),
                            credentialSigningAlgValuesSupported = setOf("EdDSA", "ES256", "ES256K", "RSA"),
                            types = types
                        )
                    }.entries
                }

                else -> error("Entry in credential issuer metadata has to be simple type list or advanced type specification, for entry: $entry")
            }
        }.associate { it.toPair() }
    }
}

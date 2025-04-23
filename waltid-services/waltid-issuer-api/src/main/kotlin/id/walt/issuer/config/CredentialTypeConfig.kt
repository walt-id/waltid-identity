package id.walt.issuer.config

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.WaltConfig
import id.walt.mdoc.doc.MDocTypes
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.*
import id.walt.sdjwt.SDJWTVCTypeMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private fun vc(vararg extra: String) = JsonArray(listOf(*extra).map { JsonPrimitive(it) })
private fun vc(credentialSupported: CredentialSupported) = Json.encodeToJsonElement(credentialSupported)
private var baseUrl = ConfigManager.getConfig<OIDCIssuerServiceConfig>().baseUrl  + "/${OpenID4VCIVersion.DRAFT13.versionString}"

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
        "IdentityCredential" to vc("VerifiableCredential", "VerifiableAttestation", "IdentityCredential"),
        "Iso18013DriversLicenseCredential" to vc("VerifiableCredential", "VerifiableAttestation", "Iso18013DriversLicenseCredential"),
        "TaxReceipt" to vc("VerifiableCredential", "VerifiableAttestation", "TaxReceipt"),
        "VerifiablePortableDocumentA1" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiablePortableDocumentA1"),
        "Visa" to vc("VerifiableCredential", "VerifiableAttestation", "Visa"),
        "eID" to vc("VerifiableCredential", "VerifiableAttestation", "eID"),
        "NaturalPersonVerifiableID" to vc("VerifiableCredential", "VerifiableAttestation", "NaturalPersonVerifiableID"),
        "BoardingPass" to vc("VerifiableCredential", "VerifiableAttestation", "BoardingPass"),
        "LegalPerson" to vc("VerifiableCredential", "LegalPerson"),
        "LegalRegistrationNumber" to vc("VerifiableCredential", "LegalRegistrationNumber"),
        "GaiaXTermsAndConditions" to vc("VerifiableCredential", "GaiaXTermsAndConditions"),

        MDocTypes.ISO_MDL to vc(
            CredentialSupported(
                format = CredentialFormat.mso_mdoc,
                cryptographicBindingMethodsSupported = setOf("cose_key"),
                credentialSigningAlgValuesSupported = setOf("ES256"),
                proofTypesSupported = mapOf(ProofType.cwt to ProofTypeMetadata(setOf("ES256"))),
                credentialDefinition =  CredentialDefinition(type = listOf(MDocTypes.ISO_MDL)),
                docType = MDocTypes.ISO_MDL
            )
        ),
        "testCredential+jwt-vc-json" to vc(
            CredentialSupported(
                format = CredentialFormat.jwt_vc_json,
                cryptographicBindingMethodsSupported = setOf("did"),
                credentialSigningAlgValuesSupported = setOf("EdDSA", "ES256", "ES256K", "RSA"),
                credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "TestCredential")),
                display = listOf(
                    DisplayProperties(
                        name = "Test Credential",
                        locale = "en-US",
                        description = "This is a test credential",
                        logo = LogoProperties(
                            url = "https://example.com/logo.png",
                            altText = "Logo"
                        ),
                        backgroundColor = "#FFFFFF",
                        textColor = "#000000",
                        backgroundImage = LogoProperties(
                            url = "https://example.com/background.png",
                            altText = "Background"
                        )
                    )
                ),
            )
        ),
        "testCredential+sd-jwt" to vc(
            CredentialSupported(
                format = CredentialFormat.jwt_vc_json,
                cryptographicBindingMethodsSupported = setOf("did"),
                credentialSigningAlgValuesSupported = setOf("EdDSA", "ES256", "ES256K", "RSA"),
                vct = baseUrl.plus("/identity_credential"),
                sdJwtVcTypeMetadata = SDJWTVCTypeMetadata(
                    vct = baseUrl.plus("/identity_credential"),
                    name = "Identity Credential",
                    description = "The Identity Verifiable Credential"
                ),
                display = listOf(
                    DisplayProperties(
                        name = "Test Credential",
                        locale = "en-US",
                        description = "This is a test credential",
                        logo = LogoProperties(
                            url = "https://example.com/logo.png",
                            altText = "Logo"
                        ),
                        backgroundColor = "#FFFFFF",
                        textColor = "#000000"
                    )
                ),
            )
        ),
        "urn:eu.europa.ec.eudi:pid:1" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("jwk"),
                credentialSigningAlgValuesSupported = setOf("ES256"),
                vct = baseUrl.plus("/urn:eu.europa.ec.eudi:pid:1")
            )
        ),
        "identity_credential_vc+sd-jwt" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("jwk"),
                credentialSigningAlgValuesSupported = setOf("ES256"),
                vct = baseUrl.plus("/identity_credential"),
                /*display = listOf( // <-- Breaks EBSI draft11 compatibility. Instead, configure in credential-issuer-metadata.conf
                    DisplayProperties(
                        name = "Test Credential",
                        locale = "en-US",
                        description = "This is a test credential",
                        logo = LogoProperties(
                            url = "https://example.com/logo.png",
                            altText = "Logo"
                        ),
                        backgroundColor = "#FFFFFF",
                        textColor = "#000000"
                    )
                ),*/
                sdJwtVcTypeMetadata =  SDJWTVCTypeMetadata(vct = baseUrl.plus("/identity_credential"), name = "Identity Credential", description = "The Identity Verifiable Credential")
            )
        ),
        "my_custom_vct_vc+sd-jwt" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("did", "jwk"),
                credentialSigningAlgValuesSupported = setOf("ES256"),
                vct = "https://example.com/my_custom_vct",
                sdJwtVcTypeMetadata = SDJWTVCTypeMetadata(
                    vct = "https://example.com/my_custom_vct",
                    name = "THE vct VALUE SHOULD BE UPDATED TO A RESOLVABLE AUTHORITY DOMAIN",
                    description = """
                        This is an example to show that custom VCT 'registries' could also be used here.
                        Warning! Example purpose only. Not intended for real use.
                    """.trimIndent()
                ),
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
                    val type = element.jsonArray.map { it.jsonPrimitive.content }

                    CredentialFormat.entries.associate { format ->
                        "${entry.key}_${format.value}" to CredentialSupported(
                            format = format,
                            cryptographicBindingMethodsSupported = if (format == CredentialFormat.sd_jwt_vc) setOf("jwk") else setOf("did"),
                            credentialSigningAlgValuesSupported = setOf("EdDSA", "ES256", "ES256K", "RSA"),
                            credentialDefinition = if (format != CredentialFormat.sd_jwt_vc && format != CredentialFormat.mso_mdoc ) CredentialDefinition(type = type)  else null,
                            vct = if (format == CredentialFormat.sd_jwt_vc) baseUrl.plus("/${entry.key}") else null,
                            docType = if (format == CredentialFormat.mso_mdoc) MDocTypes.ISO_MDL else null
                        )
                    }.entries
                }

                else -> error("Entry in credential issuer metadata has to be simple type list or advanced type specification, for entry: $entry")
            }
        }.associate { it.toPair() }
    }
}

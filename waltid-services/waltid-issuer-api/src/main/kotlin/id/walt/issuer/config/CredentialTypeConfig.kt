package id.walt.issuer.config

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.WaltConfig
import id.walt.mdoc.doc.MDocTypes
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.*
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private fun vc(vararg extra: String) = JsonArray(listOf(*extra).map { JsonPrimitive(it) })
private fun vc(credentialSupported: CredentialSupported) = Json.encodeToJsonElement(credentialSupported)
private var baseUrl =
    ConfigManager.getConfig<OIDCIssuerServiceConfig>().baseUrl + "/${OpenID4VCIVersion.DRAFT13.versionString}"

@Serializable
data class CredentialTypeConfig(
    val supportedCredentialTypes: Map<String, JsonElement> = mapOf(
        "BankId" to vc("VerifiableCredential", "BankId"),
        "KycChecksCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycChecksCredential"),
        "KycCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycCredential"),
        "KycDataCredential" to vc("VerifiableCredential", "VerifiableAttestation", "KycDataCredential"),
        "PassportCh" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "PassportCh"),
        "PND91Credential" to vc("VerifiableCredential", "PND91Credential"),
        "MortgageEligibility" to vc(
            "VerifiableCredential",
            "VerifiableAttestation",
            "VerifiableId",
            "MortgageEligibility"
        ),
        "PortableDocumentA1" to vc("VerifiableCredential", "VerifiableAttestation", "PortableDocumentA1"),
        "OpenBadgeCredential" to vc("VerifiableCredential", "OpenBadgeCredential"),
        "VaccinationCertificate" to vc("VerifiableCredential", "VerifiableAttestation", "VaccinationCertificate"),
        "WalletHolderCredential" to vc("VerifiableCredential", "WalletHolderCredential"),
        "UniversityDegree" to vc("VerifiableCredential", "UniversityDegree"),
        "VerifiableId" to vc("VerifiableCredential", "VerifiableAttestation", "VerifiableId"),
        "CTWalletSameAuthorisedInTime" to vc(
            "VerifiableCredential",
            "VerifiableAttestation",
            "CTWalletSameAuthorisedInTime"
        ),
        "CTWalletSameAuthorisedDeferred" to vc(
            "VerifiableCredential",
            "VerifiableAttestation",
            "CTWalletSameAuthorisedDeferred"
        ),
        "CTWalletSamePreAuthorisedInTime" to vc(
            "VerifiableCredential",
            "VerifiableAttestation",
            "CTWalletSamePreAuthorisedInTime"
        ),
        "CTWalletSamePreAuthorisedDeferred" to vc(
            "VerifiableCredential",
            "VerifiableAttestation",
            "CTWalletSamePreAuthorisedDeferred"
        ),
        "InTimeIssuance" to vc("VerifiableCredential", "VerifiableAttestation", "InTimeIssuance"),
        "DeferredIssuance" to vc("VerifiableCredential", "VerifiableAttestation", "DeferredIssuance"),
        "PreAuthIssuance" to vc("VerifiableCredential", "VerifiableAttestation", "PreAuthIssuance"),
        "AlpsTourReservation" to vc("VerifiableCredential", "VerifiableAttestation", "AlpsTourReservation"),
        "EducationalID" to vc("VerifiableCredential", "VerifiableAttestation", "EducationalID"),
        "HotelReservation" to vc("VerifiableCredential", "VerifiableAttestation", "HotelReservation"),
        "IdentityCredential" to vc("VerifiableCredential", "VerifiableAttestation", "IdentityCredential"),
        "Iso18013DriversLicenseCredential" to vc(
            "VerifiableCredential",
            "VerifiableAttestation",
            "Iso18013DriversLicenseCredential"
        ),
        "TaxReceipt" to vc("VerifiableCredential", "VerifiableAttestation", "TaxReceipt"),
        "VerifiablePortableDocumentA1" to vc(
            "VerifiableCredential",
            "VerifiableAttestation",
            "VerifiablePortableDocumentA1"
        ),
        "Visa" to vc("VerifiableCredential", "VerifiableAttestation", "Visa"),
        "eID" to vc("VerifiableCredential", "VerifiableAttestation", "eID"),
        "TaxCredential" to vc("VerifiableCredential", "TaxCredential"),
        "NaturalPersonVerifiableID" to vc("VerifiableCredential", "VerifiableAttestation", "NaturalPersonVerifiableID"),
        "BoardingPass" to vc("VerifiableCredential", "VerifiableAttestation", "BoardingPass"),
        "LegalPerson" to vc("VerifiableCredential", "LegalPerson"),
        "LegalRegistrationNumber" to vc("VerifiableCredential", "LegalRegistrationNumber"),
        "GaiaXTermsAndConditions" to vc("VerifiableCredential", "GaiaXTermsAndConditions"),
        "DataspaceParticipantCredential" to vc("VerifiableCredential", "DataspaceParticipantCredential"),
        "ProofOfAddressCredential" to vc("VerifiableCredential", "ProofOfAddressCredential"),
        "ePassportCredential" to vc("VerifiableCredential", "ePassportCredential"),
        "KiwiAccessCredential_jwt_vc_json" to vc(
            CredentialSupported(
                format = CredentialFormat.jwt_vc_json,
                cryptographicBindingMethodsSupported = setOf("did"),
                credentialSigningAlgValuesSupported = setOf(
                    CredSignAlgValues.Named("EdDSA"),
                    CredSignAlgValues.Named("ES256"),
                    CredSignAlgValues.Named("ES256K"),
                    CredSignAlgValues.Named("RSA")
                ),

                display = listOf(
                    DisplayProperties(
                        name = "Kiwi Access Card",
                        locale = "en-US",
                        description = "An official evidence of age and identity card for use across New Zealand.",
                        logo = LogoProperties(
                            url = "https://kiwiaccess.co.nz/wp-content/uploads/2018/10/Kiwi-Access-Logo-White.png",
                            altText = "Logo"
                        ),
                        backgroundColor = "#FFFFFF",
                        textColor = "#78350f",
                        backgroundImage = LogoProperties(
                            url = "https://e-com.demo.walt.id/img/credential-bg.png",
                            altText = "Background"
                        ),
                    )
                ),
                credentialDefinition = CredentialDefinition(
                    type = listOf(
                        "VerifiableCredential",
                        "VerifiableAttestation",
                        "KiwiAccessCredential"
                    )
                )
            )
        ),
        MDocTypes.ISO_MDL to vc(
            CredentialSupported(
                format = CredentialFormat.mso_mdoc,
                cryptographicBindingMethodsSupported = setOf("cose_key"),
                credentialSigningAlgValuesSupported = setOf(CredSignAlgValues.Named("ES256")),
                proofTypesSupported = mapOf(ProofType.cwt to ProofTypeMetadata(setOf("ES256"))),
                credentialDefinition = CredentialDefinition(type = listOf(MDocTypes.ISO_MDL)),
                docType = MDocTypes.ISO_MDL
            )
        ),
        "urn:eu.eur1opa.ec.eudi:pid:1" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("jwk"),
                credentialSigningAlgValuesSupported = setOf(CredSignAlgValues.Named("ES256")),
                vct = baseUrl.plus("/urn:eu.europa.ec.eudi:pid:1")
            )
        ),
        "identity_credential_vc+sd-jwt" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("jwk"),
                credentialSigningAlgValuesSupported = setOf(CredSignAlgValues.Named("ES256")),
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
                sdJwtVcTypeMetadata = SdJwtVcTypeMetadataDraft04(
                    name = "Identity Credential",
                    description = "The Identity Verifiable Credential"
                )
            )
        ),
        "my_custom_vct_vc+sd-jwt" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("did", "jwk"),
                credentialSigningAlgValuesSupported = setOf(CredSignAlgValues.Named("ES256")),
                vct = "https://example.com/my_custom_vct",
                sdJwtVcTypeMetadata = SdJwtVcTypeMetadataDraft04(
                    name = "THE vct VALUE SHOULD BE UPDATED TO A RESOLVABLE AUTHORITY DOMAIN",
                    description = """
                        This is an example to show that custom VCT 'registries' could also be used here.
                        Warning! Example purpose only. Not intended for real use.
                    """.trimIndent()
                ),
            )
        ),
        "photoID_credential_vc+sd-jwt" to vc(
            CredentialSupported(
                format = CredentialFormat.sd_jwt_vc,
                cryptographicBindingMethodsSupported = setOf("jwk"),
                credentialSigningAlgValuesSupported = setOf(CredSignAlgValues.Named("ES256")),
                vct = baseUrl + "/PhotoIDCredential",
                sdJwtVcTypeMetadata = SdJwtVcTypeMetadataDraft04(
                    name = "PhotoID VC (ISO 23220‑4)",
                    description = "SD‑JWT Verifiable Credential based on Photo ID schema 1.0 (ISO 23220‑4 compliant)"
                ),
                credentialDefinition = CredentialDefinition(
                    type = listOf("VerifiableCredential", "VerifiableAttestation", "PhotoIDCredential")
                )
            )
        ),

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

                    CredentialFormat.entries.minus(CredentialFormat.mso_mdoc).associate { format ->
                        "${entry.key}_${format.value}" to CredentialSupported(
                            format = format,
                            cryptographicBindingMethodsSupported = if (format == CredentialFormat.sd_jwt_vc) setOf("jwk") else setOf(
                                "did"
                            ),
                            credentialSigningAlgValuesSupported = setOf(
                                CredSignAlgValues.Named("EdDSA"),
                                CredSignAlgValues.Named("ES256"),
                                CredSignAlgValues.Named("ES256K"),
                                CredSignAlgValues.Named("RSA")
                            ),
                            credentialDefinition = if (format != CredentialFormat.sd_jwt_vc && format != CredentialFormat.mso_mdoc) CredentialDefinition(
                                type = type
                            ) else null,
                            vct = if (format == CredentialFormat.sd_jwt_vc) baseUrl.plus("/${entry.key}") else null,
                        )
                    }.entries
                }

                else -> error("Entry in credential issuer metadata has to be simple type list or advanced type specification, for entry: $entry")
            }
        }.associate { it.toPair() }
    }
}

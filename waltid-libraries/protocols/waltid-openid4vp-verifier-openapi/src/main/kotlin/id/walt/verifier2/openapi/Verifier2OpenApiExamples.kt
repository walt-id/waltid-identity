package id.walt.verifier2.openapi

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.NoMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.*
import io.ktor.http.Url
import id.walt.policies2.vc.policies.status.Values
import id.walt.policies2.vc.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.W3CStatusPolicyListArguments
import id.walt.policies2.vp.policies.*
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.DcApiAnnexDFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.OpenId4VPConfig
import id.walt.verifier2.data.UrlConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.DefinedVerificationPolicies
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Verifier2OpenApiExamples {

    private const val IDENTITY_CREDENTIAL_VCT =
        "http://waltid.enterprise.localhost:3000/v1/waltid.issuer/issuer-service-api/openid4vc/draft13/identity_credential"

    val openid4vpHttpW3cVcDefault = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpW3cVcBasic = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        CredentialSignaturePolicy(),
                        ExpirationDatePolicy(),
                        NotBeforePolicy(),
                        AllowedIssuerPolicy(JsonArray(listOf(JsonPrimitive("https://university.example/issuers/565049")))),
                        RegexPolicy(
                            path = "$.credentialSubject.degree.name",
                            regex = "^Bachelor of Science and Arts$"
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpMdocCredentialStatusTokenStatusList = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_mdl",
                        format = CredentialFormat.MSO_MDOC,
                        meta = MsoMdocMeta(
                            doctypeValue = "org.iso.18013.5.1.mDL"
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name")),
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "given_name"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        StatusPolicy(
                            argument = IETFStatusPolicyAttribute(
                                value = 0u
                            )
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpMdocCredentialStatusTokenStatusListMultipleValues = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_mdl",
                        format = CredentialFormat.MSO_MDOC,
                        meta = MsoMdocMeta(
                            doctypeValue = "org.iso.18013.5.1.mDL"
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name")),
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "given_name"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        StatusPolicy(
                            argument = IETFStatusPolicyAttribute(
                                values = listOf(0u, 1u)
                            )
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpW3cVcCredentialStatusBitstringStatusList = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        StatusPolicy(
                            argument = W3CStatusPolicyAttribute(
                                value = 0u,
                                purpose = "Revocation",
                                type = Values.BITSTRING_STATUS_LIST
                            )
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpW3cVcCredentialStatusBitstringStatusListMultipleValues = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        StatusPolicy(
                            argument = W3CStatusPolicyAttribute(
                                values = listOf(0u, 1u),
                                purpose = "Revocation",
                                type = Values.BITSTRING_STATUS_LIST
                            )
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpW3cVcCredentialStatusMultipleBitstringStatusList = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        StatusPolicy(
                            argument = W3CStatusPolicyListArguments(
                                list = listOf(
                                    W3CStatusPolicyAttribute(
                                        value = 0u,
                                        purpose = "Revocation",
                                        type = Values.BITSTRING_STATUS_LIST
                                    ),
                                    W3CStatusPolicyAttribute(
                                        value = 0u,
                                        purpose = "Suspension",
                                        type = Values.BITSTRING_STATUS_LIST
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpW3cVcWebhook = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        WebhookPolicy("http://your-backend.com")
                    )
                )
            )
        )
    )

    val openid4vpHttpW3cVcPresentation = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vp_policies = VPPolicyList(
                    jwtVcJson = listOf(
                        AudienceCheckJwtVcJsonVPPolicy(),
                        NonceCheckJwtVcJsonVPPolicy(),
                        SignatureJwtVcJsonVPPolicy()
                    ),
                    dcSdJwt = listOf(),
                    msoMdoc = listOf()
                )
            )
        )
    )

    // IETF SD-JWT VC Examples
    val openid4vpHttpSdJwtVcDefault = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "pid", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(
                            vctValues = listOf(IDENTITY_CREDENTIAL_VCT)
                        ), claims = listOf(
                            ClaimsQuery(pathStrings = listOf("given_name")),
                            ClaimsQuery(pathStrings = listOf("family_name")),
                            ClaimsQuery(pathStrings = listOf("address", "street_address"))
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpSdJwtVcBasic = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "pid", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(
                            vctValues = listOf(IDENTITY_CREDENTIAL_VCT)
                        ), claims = listOf(
                            ClaimsQuery(pathStrings = listOf("given_name")),
                            ClaimsQuery(pathStrings = listOf("family_name")),
                            ClaimsQuery(pathStrings = listOf("address", "street_address"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        CredentialSignaturePolicy(),
                        ExpirationDatePolicy(),
                        NotBeforePolicy(),
                        AllowedIssuerPolicy(JsonArray(listOf(JsonPrimitive("https://university.example/issuers/565049")))),
                        RegexPolicy(
                            path = "$.credentialSubject.degree.name",
                            regex = "^Bachelor of Science and Arts$"
                        )
                    )
                )
            )
        )
    )

    val openid4vpHttpSdJwtVcPresentation = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "pid", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(
                            vctValues = listOf(IDENTITY_CREDENTIAL_VCT)
                        ), claims = listOf(
                            ClaimsQuery(pathStrings = listOf("given_name")),
                            ClaimsQuery(pathStrings = listOf("family_name")),
                            ClaimsQuery(pathStrings = listOf("address", "street_address"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vp_policies = VPPolicyList(
                    jwtVcJson = listOf(),
                    dcSdJwt = listOf(
                        AudienceCheckSdJwtVPPolicy(),
                        KbJwtSignatureSdJwtVPPolicy(),
                        NonceCheckSdJwtVPPolicy(),
                        SdHashCheckSdJwtVPPolicy()
                    ),
                    msoMdoc = listOf()
                )
            )
        )
    )

    val openid4vpHttpSdJwtVcTransactionData = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "pid",
                        format = CredentialFormat.DC_SD_JWT,
                        meta = SdJwtVcMeta(
                            vctValues = listOf(IDENTITY_CREDENTIAL_VCT)
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("given_name")),
                            ClaimsQuery(pathStrings = listOf("family_name")),
                        )
                    )
                )
            )
        ),
        openid = OpenId4VPConfig(
            transactionData = listOf(
                buildJsonObject {
                    put("type", JsonPrimitive("org.waltid.transaction-data.payment-authorization"))
                    put("credential_ids", JsonArray(listOf(JsonPrimitive("pid"))))
                    put("require_cryptographic_holder_binding", JsonPrimitive(true))
                    put("transaction_data_hashes_alg", JsonArray(listOf(JsonPrimitive("sha-256"))))
                    put("amount", JsonPrimitive("42.00"))
                    put("currency", JsonPrimitive("EUR"))
                    put("payee", JsonPrimitive("ACME Corp"))
                    put("reference", JsonPrimitive("INV-2026-042"))
                }
            )
        )
    )

    private const val SCA_PAYMENT_CARD_DOCTYPE = "eu.europa.ec.eudi.sca.payment_card.1"

    /** The demo SCA payment card mdoc, as issuer2's `scaPaymentCardMdoc` profile issues it. */
    private val scaPaymentCardCredentialQuery = CredentialQuery(
        id = "sca_payment_card",
        format = CredentialFormat.MSO_MDOC,
        meta = MsoMdocMeta(doctypeValue = SCA_PAYMENT_CARD_DOCTYPE),
        claims = listOf(
            ClaimsQuery(pathStrings = listOf(SCA_PAYMENT_CARD_DOCTYPE, "card_scheme")),
            ClaimsQuery(pathStrings = listOf(SCA_PAYMENT_CARD_DOCTYPE, "card_last4")),
            ClaimsQuery(pathStrings = listOf(SCA_PAYMENT_CARD_DOCTYPE, "pan_reference")),
            ClaimsQuery(pathStrings = listOf(SCA_PAYMENT_CARD_DOCTYPE, "card_holder_name")),
            ClaimsQuery(pathStrings = listOf(SCA_PAYMENT_CARD_DOCTYPE, "expiry_date")),
        )
    )

    /**
     * One `urn:eudi:sca:payment:1` entry bound to [scaPaymentCardCredentialQuery], carrying the nested
     * `transaction_id`, `payee` (`name` and `id`), `currency` and numeric `amount` payload of the EUDI
     * TS-12 payment data model. `amount` is a JSON number, which is how the type defines it.
     */
    private val scaPaymentTransactionData = OpenId4VPConfig(
        transactionData = listOf(
            buildJsonObject {
                put("type", "urn:eudi:sca:payment:1")
                put("credential_ids", JsonArray(listOf(JsonPrimitive("sca_payment_card"))))
                put("require_cryptographic_holder_binding", true)
                put("transaction_data_hashes_alg", JsonArray(listOf(JsonPrimitive("sha-256"))))
                put(
                    "payload",
                    buildJsonObject {
                        put("transaction_id", "8D8AC610-566D-4EF0-9C22-186B2A5ED793")
                        put(
                            "payee",
                            buildJsonObject {
                                put("name", "Super Store")
                                put("id", "merchant-001")
                            }
                        )
                        put("currency", "EUR")
                        put("amount", 11.56)
                    }
                )
            }
        )
    )

    /**
     * SCA payment over DC API: one demo SCA payment card mdoc plus `urn:eudi:sca:payment:1`
     * transaction data.
     *
     * Requesting a single credential is what makes this the SCA example that runs through Android
     * Credential Manager - see [openid4vpDcApiScaPaymentCardAndAgeVerificationScaPayment] for why the
     * combined one does not. Transaction data on an mdoc additionally has to be authorized at
     * issuance: the type must appear in the credential's MSO `KeyAuthorizations` for the holder to
     * sign it, which issuer2's `scaPaymentCardMdoc` profile does.
     */
    val openid4vpDcApiScaPaymentCardScaPayment = DcApiAnnexDFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(credentials = listOf(scaPaymentCardCredentialQuery)),
            signedRequest = false,
            encryptedResponse = false,
        ),
        expectedOrigins = listOf("https://digital-credentials.walt.id"),
        haip = false,
        openid = scaPaymentTransactionData,
    )

    /**
     * SCA payment card + EU age verification over DC API, with `urn:eudi:sca:payment:1` transaction
     * data bound to the payment card credential. Combined presentation - payment attributes plus a
     * second, non-payment credential in one request - is a reference use case worth having a request
     * shape for. The transaction data binds to the payment card alone: `credential_ids` names only it,
     * so the age credential is presented without device-signing the transaction data hash.
     *
     * **Not presentable through Android Credential Manager**, in this or any other shape; use
     * [openid4vpDcApiScaPaymentCardScaPayment] there. The limitation is in the matcher AndroidX
     * embeds, not in the request: it compares a candidate only against the *first* entry of
     * `transaction_data[0].credential_ids`, and skips transaction data entirely unless
     * `transaction_data` holds exactly one entry. Combining transaction data with a second credential
     * therefore yields zero candidates, and splitting into one entry per credential surfaces both
     * credentials but renders no transaction data. The matcher's transaction-data handling never
     * learns a candidate's format, so no credential format escapes it. Browsers on other platforms,
     * and the wallet's own review screen, are unaffected: they build the prompt from the request.
     */
    val openid4vpDcApiScaPaymentCardAndAgeVerificationScaPayment = DcApiAnnexDFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    scaPaymentCardCredentialQuery,
                    CredentialQuery(
                        id = "proof_of_age",
                        format = CredentialFormat.MSO_MDOC,
                        meta = MsoMdocMeta(doctypeValue = "eu.europa.ec.av.1"),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("eu.europa.ec.av.1", "age_over_18")),
                        )
                    ),
                )
            ),
            signedRequest = false,
            encryptedResponse = false,
        ),
        expectedOrigins = listOf("https://digital-credentials.walt.id"),
        haip = false,
        openid = scaPaymentTransactionData,
    )

    // ISO Examples

    val openid4vpHttpIsoPhotoIdMinimal = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "my_photoid",
                        format = CredentialFormat.MSO_MDOC,
                        meta = MsoMdocMeta(
                            doctypeValue = "org.iso.23220.photoid.1"
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name_unicode")),
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "given_name_unicode")),
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "issuing_authority_unicode")),
                            ClaimsQuery(
                                pathStrings = listOf("org.iso.18013.5.1", "issuing_country"),
                                values = listOf("AT").map { JsonPrimitive(it) }
                            ),
                            ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "travel_document_number"))
                        )
                    )
                )
            )
        )
    )


    val openid4vpHttpIsoPhotoIdVical = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "my_photoid",
                        format = CredentialFormat.MSO_MDOC,
                        meta = MsoMdocMeta(
                            doctypeValue = "org.iso.23220.photoid.1"
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name_unicode")),
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "given_name_unicode")),
                            ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "issuing_authority_unicode")),
                            ClaimsQuery(
                                pathStrings = listOf("org.iso.18013.5.1", "resident_postal_code"),
                                values = listOf(1180, 1190, 1200, 1210).map { JsonPrimitive(it) }
                            ),
                            ClaimsQuery(
                                pathStrings = listOf("org.iso.18013.5.1", "issuing_country"),
                                values = listOf("AT").map { JsonPrimitive(it) }
                            ),
                            ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "person_id")),
                            ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "resident_street")),
                            ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "administrative_number")),
                            ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "travel_document_number")),
                            ClaimsQuery(pathStrings = listOf("org.iso.23220.dtc.1", "dtc_version")),
                            ClaimsQuery(pathStrings = listOf("org.iso.23220.dtc.1", "dtc_dg1"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    listOf(
                        CredentialSignaturePolicy(),
                        VicalPolicy(
                            vical = "<base64 encoded VICAL file>",
                            enableDocumentTypeValidation = true,
                            enableTrustedChainRoot = true,
                            enableSystemTrustAnchors = true,
                            enableRevocation = true
                        )
                    )
                )
            )
        )
    )


    // OLD EXAMPLES BELOW

    val basicExample = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "pid", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(
                            vctValues = listOf(IDENTITY_CREDENTIAL_VCT)
                        ), claims = listOf(
                            ClaimsQuery(pathStrings = listOf("given_name")),
                            ClaimsQuery(pathStrings = listOf("family_name")),
                            ClaimsQuery(pathStrings = listOf("address", "street_address"))
                        )
                    )
                )
            )
        ),
        urlConfig = UrlConfig(),
        redirects = Verification2Session.VerificationSessionRedirects(
            successRedirectUri = Url("https://example.com/verification-successful")
        )
    )


    val w3cPlusPath = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            )
        )
    )

    val emptyMeta = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = NoMeta,
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            )
        )
    )

    val nestedPresentationRequestW3C = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(
                                listOf("VerifiableCredential", "OpenBadgeCredential")
                            )
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("credentialSubject", "achievement", "description"))
                        )
                    )
                )
            )
        )
    )

    val nestedPresentationRequestWithMultipleClaims = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(
                                listOf("VerifiableCredential", "OpenBadgeCredential")
                            )
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("credentialSubject", "achievement", "description")),
                            ClaimsQuery(pathStrings = listOf("credentialSubject", "achievement", "criteria", "type")),
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            )
        )
    )

    val w3cTypeValues = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(
                                listOf("OpenBadgeCredential")
                            )
                        ),
                        claims = listOf(
                            ClaimsQuery(pathStrings = listOf("name"))
                        )
                    )
                )
            )
        )
    )

    val W3CWithoutClaims = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(
                                listOf("OpenBadgeCredential")
                            )
                        )
                    )
                )
            )
        )
    )

    val W3CWithClaimsAndValues = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(
                                listOf("VerifiableCredential", "OpenBadgeCredential")
                            )
                        ),
                        claims = listOf(
                            ClaimsQuery(
                                pathStrings = listOf("name"),
                                values = listOf(JsonPrimitive("JFF x vc-edu PlugFest 3 Interoperability"))
                            )
                        )
                    )
                )
            )
        )
    )


    val basicExampleWithStatusPolicyForTokenStatusList = basicExample.copy(
        core = basicExample.core.copy(
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    policies = listOf(
                        StatusPolicy(
                            argument = IETFStatusPolicyAttribute(
                                value = 0u
                            )
                        )
                    )
                )
            )
        )
    )

    val w3cCredentialQuery = CredentialQuery(
        id = "pid", format = CredentialFormat.JWT_VC_JSON, meta = JwtVcJsonMeta(
            typeValues = listOf(listOf("VerifiableCredential", "identity_credential"))
        ), claims = listOf(
            ClaimsQuery(pathStrings = listOf("given_name")),
            ClaimsQuery(pathStrings = listOf("family_name")),
            ClaimsQuery(pathStrings = listOf("address", "street_address"))
        )
    )

    val basicExampleWithRevokedStatusListPolicy = basicExample.copy(
        core = basicExample.core.copy(
            dcqlQuery = DcqlQuery(
                credentials = listOf(w3cCredentialQuery)
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    policies = listOf(RevocationPolicy())
                )
            )
        )
    )

    val basicExampleWithStatusPolicyForSingleBitstringStatusList = basicExample.copy(
        core = basicExample.core.copy(
            dcqlQuery = DcqlQuery(
                credentials = listOf(w3cCredentialQuery)
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    policies = listOf(
                        StatusPolicy(
                            argument = W3CStatusPolicyAttribute(
                                value = 0u, purpose = "Revocation", type = Values.BITSTRING_STATUS_LIST
                            )
                        )
                    )
                )
            )
        )
    )

    val basicExampleWithStatusPolicyForMultipleBitstringStatusList = basicExample.copy(
        core = basicExample.core.copy(
            dcqlQuery = DcqlQuery(
                credentials = listOf(w3cCredentialQuery)
            ),
            policies = DefinedVerificationPolicies(
                vc_policies = VCPolicyList(
                    policies = listOf(
                        StatusPolicy(
                            argument = W3CStatusPolicyListArguments(
                                list = listOf(
                                    W3CStatusPolicyAttribute(
                                        value = 0u, purpose = "Revocation", type = Values.BITSTRING_STATUS_LIST
                                    ), W3CStatusPolicyAttribute(
                                        value = 0u, purpose = "Suspension", type = Values.BITSTRING_STATUS_LIST
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

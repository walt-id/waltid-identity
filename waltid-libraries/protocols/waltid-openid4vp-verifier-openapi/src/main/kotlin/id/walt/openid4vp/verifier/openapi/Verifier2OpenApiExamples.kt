package id.walt.openid4vp.verifier.openapi

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.NoMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.openid4vp.verifier.data.CrossDeviceFlowSetup
import id.walt.openid4vp.verifier.data.GeneralFlowConfig
import id.walt.openid4vp.verifier.data.UrlConfig
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.data.Verification2Session.DefinedVerificationPolicies
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.AllowedIssuerPolicy
import id.walt.policies2.vc.policies.RegexPolicy
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.ExpirationDatePolicy
import id.walt.policies2.vc.policies.NotBeforePolicy
import id.walt.policies2.vc.policies.RevocationPolicy
import id.walt.policies2.vc.policies.StatusPolicy
import id.walt.policies2.vc.policies.VicalPolicy
import id.walt.policies2.vc.policies.WebhookPolicy
import id.walt.policies2.vc.policies.status.Values
import id.walt.policies2.vc.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.W3CStatusPolicyListArguments
import id.walt.policies2.vp.policies.AudienceCheckJwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.AudienceCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.KbJwtSignatureSdJwtVPPolicy
import id.walt.policies2.vp.policies.NonceCheckJwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.NonceCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.SdHashCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.SignatureJwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.VPPolicyList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object Verifier2OpenApiExamples {

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
                            ClaimsQuery(path = listOf("name"))
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
                            ClaimsQuery(path = listOf("name"))
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

    val openid4vpHttpW3cVcCredentialStatusTokenStatusList = CrossDeviceFlowSetup(
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
                            ClaimsQuery(path = listOf("name"))
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
                            ClaimsQuery(path = listOf("name"))
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
                            ClaimsQuery(path = listOf("name"))
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
                            ClaimsQuery(path = listOf("name"))
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
                            ClaimsQuery(path = listOf("name"))
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
                            vctValues = listOf("http://waltid.enterprise.localhost:3000/v1/waltid.issuer/issuer-service-api/openid4vc/draft13/identity_credential")
                        ), claims = listOf(
                            ClaimsQuery(path = listOf("given_name")),
                            ClaimsQuery(path = listOf("family_name")),
                            ClaimsQuery(path = listOf("address", "street_address"))
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
                            vctValues = listOf("http://waltid.enterprise.localhost:3000/v1/waltid.issuer/issuer-service-api/openid4vc/draft13/identity_credential")
                        ), claims = listOf(
                            ClaimsQuery(path = listOf("given_name")),
                            ClaimsQuery(path = listOf("family_name")),
                            ClaimsQuery(path = listOf("address", "street_address"))
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
                            vctValues = listOf("http://waltid.enterprise.localhost:3000/v1/waltid.issuer/issuer-service-api/openid4vc/draft13/identity_credential")
                        ), claims = listOf(
                            ClaimsQuery(path = listOf("given_name")),
                            ClaimsQuery(path = listOf("family_name")),
                            ClaimsQuery(path = listOf("address", "street_address"))
                        )
                    )
                )
            ),
            policies = DefinedVerificationPolicies(
                vp_policies = VPPolicyList(
                    jwtVcJson = listOf(),
                    dcSdJwt = listOf(AudienceCheckSdJwtVPPolicy(),
                        KbJwtSignatureSdJwtVPPolicy(),
                        NonceCheckSdJwtVPPolicy(),
                        SdHashCheckSdJwtVPPolicy()),
                    msoMdoc = listOf()
                )
            )
        )
    )

    // ISO Examples


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
                            ClaimsQuery(path = listOf("org.iso.18013.5.1", "family_name_unicode")),
                            ClaimsQuery(path = listOf("org.iso.18013.5.1", "given_name_unicode")),
                            ClaimsQuery(path = listOf("org.iso.18013.5.1", "issuing_authority_unicode")),
                            ClaimsQuery(
                                path = listOf("org.iso.18013.5.1", "resident_postal_code"),
                                values = listOf(1180, 1190, 1200, 1210).map { JsonPrimitive(it) }
                            ),
                            ClaimsQuery(
                                path = listOf("org.iso.18013.5.1", "issuing_country"),
                                values = listOf("AT").map { JsonPrimitive(it) }
                            ),
                            ClaimsQuery(path = listOf("org.iso.23220.photoid.1", "person_id")),
                            ClaimsQuery(path = listOf("org.iso.23220.photoid.1", "resident_street")),
                            ClaimsQuery(path = listOf("org.iso.23220.photoid.1", "administrative_number")),
                            ClaimsQuery(path = listOf("org.iso.23220.photoid.1", "travel_document_number")),
                            ClaimsQuery(path = listOf("org.iso.23220.dtc.1", "dtc_version")),
                            ClaimsQuery(path = listOf("org.iso.23220.dtc.1", "dtc_dg1"))
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
                            vctValues = listOf("http://waltid.enterprise.localhost:3000/v1/waltid.issuer/issuer-service-api/openid4vc/draft13/identity_credential")
                        ), claims = listOf(
                            ClaimsQuery(path = listOf("given_name")),
                            ClaimsQuery(path = listOf("family_name")),
                            ClaimsQuery(path = listOf("address", "street_address"))
                        )
                    )
                )
            )
        ),
        urlConfig = UrlConfig(),
        redirects = Verification2Session.VerificationSessionRedirects(
            successRedirectUri = "https://example.com/verification-successful"
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
                            ClaimsQuery(path = listOf("name"))
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
                            ClaimsQuery(path = listOf("name"))
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
                            ClaimsQuery(path = listOf("credentialSubject", "achievement", "description"))
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
                            ClaimsQuery(path = listOf("credentialSubject", "achievement", "description")),
                            ClaimsQuery(path = listOf("credentialSubject", "achievement", "criteria", "type")),
                            ClaimsQuery(path = listOf("name"))
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
                            ClaimsQuery(path = listOf("name"))
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
                                path = listOf("name"),
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
            ClaimsQuery(path = listOf("given_name")),
            ClaimsQuery(path = listOf("family_name")),
            ClaimsQuery(path = listOf("address", "street_address"))
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

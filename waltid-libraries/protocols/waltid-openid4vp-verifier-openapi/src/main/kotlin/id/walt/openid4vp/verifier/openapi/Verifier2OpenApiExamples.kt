package id.walt.openid4vp.verifier.openapi

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.NoMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.dcql.models.meta.W3cCredentialMeta
import id.walt.openid4vp.verifier.Verification2Session.DefinedVerificationPolicies
import id.walt.openid4vp.verifier.data.*
import id.walt.policies2.PolicyList
import id.walt.policies2.policies.CredentialSignaturePolicy
import id.walt.policies2.policies.RevocationPolicy
import id.walt.policies2.policies.StatusPolicy
import id.walt.policies2.policies.VicalPolicy
import id.walt.policies2.policies.status.Values
import id.walt.policies2.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies2.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies2.policies.status.model.W3CStatusPolicyListArguments
import kotlinx.serialization.json.JsonPrimitive

object Verifier2OpenApiExamples {

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

    val VicalPolicyValues = CrossDeviceFlowSetup(
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
            policies = Verification2Session.DefinedVerificationPolicies(
                vcPolicies = PolicyList(listOf(
                    CredentialSignaturePolicy(),
                    VicalPolicy(
                        vical = "<base64 encoded VICAL file>",
                        enableDocumentTypeValidation = true,
                        enableTrustedChainRoot = true,
                        enableSystemTrustAnchors = true,
                        enableRevocation = true
                    )
                ))
            )
        )
    )

    val basicExampleWithStatusPolicyForTokenStatusList = basicExample.copy(
        policies = DefinedVerificationPolicies(
            vcPolicies = PolicyList(
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

    val w3cCredentialQuery = CredentialQuery(
        id = "pid", format = CredentialFormat.JWT_VC_JSON, meta = W3cCredentialMeta(
            typeValues = listOf(listOf("VerifiableCredential", "identity_credential"))
        ), claims = listOf(
            ClaimsQuery(path = listOf("given_name")),
            ClaimsQuery(path = listOf("family_name")),
            ClaimsQuery(path = listOf("address", "street_address"))
        )
    )

    val basicExampleWithRevokedStatusListPolicy = basicExample.copy(
        dcqlQuery = DcqlQuery(
            credentials = listOf(w3cCredentialQuery)
        ), policies = DefinedVerificationPolicies(
            vcPolicies = PolicyList(
                policies = listOf(RevocationPolicy())
            )
        )
    )

    val basicExampleWithStatusPolicyForSingleBitstringStatusList = basicExample.copy(
        dcqlQuery = DcqlQuery(
            credentials = listOf(w3cCredentialQuery)
        ), policies = DefinedVerificationPolicies(
            vcPolicies = PolicyList(
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

    val basicExampleWithStatusPolicyForMultipleBitstringStatusList = basicExample.copy(
        dcqlQuery = DcqlQuery(
            credentials = listOf(w3cCredentialQuery)
        ), policies = DefinedVerificationPolicies(
            vcPolicies = PolicyList(
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
}

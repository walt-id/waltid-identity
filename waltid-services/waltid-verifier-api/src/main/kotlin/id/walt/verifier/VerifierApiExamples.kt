package id.walt.verifier

import id.walt.commons.interop.LspPotentialInterop
import io.github.smiley4.ktorswaggerui.dsl.routes.ValueExampleDescriptorDsl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

object VerifierApiExamples {

    //todo: remove line when ktor-swagger-ui#107 is fixed
    private fun jsonObjectValueExampleDescriptorDsl(content: String): ValueExampleDescriptorDsl.() -> Unit = {
        value = Json.decodeFromString<JsonObject>(content)
    }

    // language=json
    private val vpPolicyMinMaxData = """
        [
            {
                "policy": "minimum-credentials",
                "args": 2
            },
            {
                "policy": "maximum-credentials",
                "args": 100
            }
        ]
    """.trimIndent()

    // language=json
    private val vpPolicyTypesData = """
        [
            "signature",
            "expired",
            "not-before",
            "presentation-definition"
        ]
    """.trimIndent()

    // language=json
    private fun vcPoliciesData(additional: String?=null) = let{
        """
        [
            "signature",
            "expired",
            "not-before"
            ${additional?.let { ",$it" } ?: ""}
        ]
    """.trimIndent()}//${additional.joinToString { "$it" }}

    // language=json
    private val issuerPolicyData = """
        {
            "policy": "schema",
            "args":
            {
                "type": "object",
                "required":
                [
                    "issuer"
                ],
                "properties":
                {
                    "issuer":
                    {
                        "type": "object"
                    }
                }
            }
        }
    """.trimIndent()

    // language=json
    private fun jwtFormat(alg: String) = """
        {
            "alg":
            [
                $alg
            ]
        }
    """.trimIndent()

    // Minimal call, default policies will be used, PresentationDefinition is generated based on credentials requested in `request_credentials`:
    //language=json
    val minimal = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials":
                [
                    { "format": "jwt_vc_json", "type": "OpenBadgeCredential" }
                ]
            }
        """.trimIndent()
    )

    //Call with policies for the VerifiablePresentation, default policies for VCs, generated PresentationDefinition:
    //language=json
    val vpPolicies = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "vp_policies": $vpPolicyMinMaxData,
                "request_credentials":
                [
                    { "format": "jwt_vc_json", "type": "OpenBadgeCredential" },
                    { "format": "jwt_vc_json", "type": "VerifiableId" }
                ]
            }
        """.trimIndent()
    )

    //Call with policies for the VerifiablePresentation, defined policies for all VCs, generated PresentationDefinition:
    //language=json
    val vpGlobalVcPolicies = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "vp_policies": $vpPolicyMinMaxData,
                "vc_policies": ${vcPoliciesData("\"revoked_status_list\"")},
                "request_credentials":
                [
                    { "format": "jwt_vc_json", "type": "OpenBadgeCredential" },
                    { "format": "jwt_vc_json", "type": "VerifiableId" }
                ]
            }
        """.trimIndent()
    )

    // Call with policies for the VerifiablePresentation, defined policies for all VCs, generated PresentationDefinition,
    // and special policies for each credential type:
    //language=json
    val vcVpIndividualPolicies = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "vp_policies": $vpPolicyMinMaxData,
                "vc_policies": ${vcPoliciesData("\"revoked_status_list\"")},
                "request_credentials":
                [
                    { "format": "jwt_vc_json", "type": "VerifiableId" },
                    { "format": "jwt_vc_json", "type": "ProofOfResidence" },
                    {
                        "format": "jwt_vc_json",
                        "type": "OpenBadgeCredential",
                        "policies":
                        [
                            "signature",
                            $issuerPolicyData
                        ]
                    }
                ]
            }
        """.trimIndent()
    )

    // Call with policies for the VerifiablePresentation, defined policies for all VCs, and special policies for each credential type,
    // the PresentationDefinition is not generated but manually defined:
    //language=json
    val maxExample = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "vp_policies": $vpPolicyMinMaxData,
                "vc_policies": ${vcPoliciesData("\"revoked_status_list\"")},
                "request_credentials":
                [
                    { "format": "jwt_vc_json", "type": "VerifiableId" },
                    { "format": "jwt_vc_json", "type": "ProofOfResidence" },
                    {
                        "format": "jwt_vc_json",
                        "type": "OpenBadgeCredential",
                        "policies":
                        [
                            "signature",
                            $issuerPolicyData
                        ]
                    },
                    {
                      "input_descriptor": {
                            "id": "VerifiableId",
                            "format":
                            {
                                "jwt_vc_json": ${jwtFormat("\"EdDSA\"")}
                            },
                            "constraints":
                            {
                                "fields":
                                [
                                    {
                                        "path":
                                        [
                                            "${'$'}.type"
                                        ],
                                        "filter":
                                        {
                                            "type": "string",
                                            "pattern": "VerifiableId"
                                        }
                                    }
                                ]
                            }
                        }
                    }
                ]
            }
        """.trimIndent()
    )

    //language=JSON
    val presentationDefinitionPolicy = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "vp_policies": $vpPolicyTypesData,
                "vc_policies": ${vcPoliciesData()},
                "request_credentials":
                [
                    { "format": "jwt_vc_json", "type": "ProofOfResidence" },
                    {
                        "format": "jwt_vc_json", 
                        "type":  "OpenBadgeCredential",
                        "policies":
                        [
                            "signature",
                            $issuerPolicyData
                        ]
                    }
                ]
            }
        """.trimIndent()
    )

    // language=json
    val EbsiVerifiablePDA1 = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "vc_policies": ${vcPoliciesData("\"revoked_status_list\"")},
                "request_credentials":
                [
                    { "input_descriptor": {
                            "id": "e3d700aa-0988-4eb6-b9c9-e00f4b27f1d8",
                            "constraints":
                            {
                                "fields":
                                [
                                    {
                                        "path":
                                        [
                                            "${'$'}.type"
                                        ],
                                        "filter":
                                        {
                                            "contains":
                                            {
                                                "const": "VerifiablePortableDocumentA1"
                                            },
                                            "type": "array"
                                        }
                                    }
                                ]
                            }
                        }
                    }
                ],
                "openid_profile": "EBSIv3"
            }
        """.trimIndent()
    )

    val lspPotentialMdocExample = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials": [ { "format": "mso_mdoc", "doc_type": "org.iso.18013.5.1.mDL" } ],
                "trusted_root_cas":[ ${Json.encodeToJsonElement(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT)} ],
                "openid_profile": "ISO_18013_7_MDOC"
            }

        """.trimIndent()
    )

    val lspPotentialSDJwtVCExample = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials": [ { "format": "vc+sd-jwt", "vct": "http://localhost:7002/identity_credential" } ]
            }

        """.trimIndent()
    )

    val sdJwtVCExampleWithRequiredFields = jsonObjectValueExampleDescriptorDsl(
        """
            {"request_credentials":[{"format":"vc+sd-jwt","vct":"urn:eu.europa.ec.eudi:pid:1","input_descriptor":{"id":"urn:eu.europa.ec.eudi:pid:1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["${'$'}.vct"],"filter":{"type":"string","pattern":"urn:eu.europa.ec.eudi:pid:1"}},{"path":["${'$'}.birthdate"],"filter":{"type":"string","pattern":".*"}}],"limit_disclosure":"required"}}}],"vp_policies":["signature_sd-jwt-vc","presentation-definition"],"vc_policies":["not-before","expired",{"policy":"allowed-issuer","args":"potential-lsp-issuer-key-01"}]}
        """.trimIndent()
    )
}

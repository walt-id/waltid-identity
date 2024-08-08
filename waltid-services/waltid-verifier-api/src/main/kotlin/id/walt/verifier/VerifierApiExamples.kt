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
                    "OpenBadgeCredential"
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
                    "OpenBadgeCredential",
                    "VerifiableId"
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
                    "OpenBadgeCredential",
                    "VerifiableId"
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
                    "VerifiableId",
                    "ProofOfResidence",
                    {
                        "credential": "OpenBadgeCredential",
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
                    "VerifiableId",
                    "ProofOfResidence",
                    {
                        "credential": "OpenBadgeCredential",
                        "policies":
                        [
                            "signature",
                            $issuerPolicyData
                        ]
                    }
                ],
                "presentation_definition":
                {
                    "id": "<automatically assigned>",
                    "input_descriptors":
                    [
                        {
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
                    ]
                }
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
                    "ProofOfResidence",
                    {
                        "credential": "OpenBadgeCredential",
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
                    "VerifiablePortableDocumentA1"
                ],
                "presentation_definition":
                {
                    "id": "70fc7fab-89c0-4838-ba77-4886f47c3761",
                    "input_descriptors":
                    [
                        {
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
                    ],
                    "format":
                    {
                        "jwt_vc": ${jwtFormat("\"ES256\"")},
                        "jwt_vp": ${jwtFormat("\"ES256\"")}
                    }
                }
            }
        """.trimIndent()
    )

    val lspPotentialMdocExample = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials": [ "org.iso.18013.5.1.mDL" ],
                "trusted_root_cas":[ ${Json.encodeToJsonElement(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT)} ]
            }

        """.trimIndent()
    )

    val lspPotentialSDJwtVCExample = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials": [ "identity_credential_vc+sd-jwt" ]
            }

        """.trimIndent()
    )
}

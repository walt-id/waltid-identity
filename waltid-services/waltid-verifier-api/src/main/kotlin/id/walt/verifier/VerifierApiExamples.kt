package id.walt.verifier

import io.github.smiley4.ktoropenapi.config.ValueExampleDescriptorConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

object VerifierApiExamples {

    //todo: remove line when ktor-swagger-ui#107 is fixed
    private fun jsonObjectValueExampleDescriptorDsl(content: String): ValueExampleDescriptorConfig.() -> Unit = {
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
    private val dynamiPolicy = """
        {
          "policy": "dynamic",
          "args":  {
            "policy_name": "test",
            "opa_server":"http://localhost:8181",
            "policy_query":"data",
            "rules": {
                   "rego": "package data.test\r\n\r\ndefault allow := false\r\n\r\nallow if {\r\ninput.parameter.name == input.credentialData.credentialSubject.achievement.name\r\n}"
            },
            "argument": {
                "name": "JFF x vc-edu PlugFest 3 Interoperability"
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

    val EBSIVectorExampleInTimeIssuance = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials":
                [
                    { "format": "jwt_vc", "type": "InTimeIssuance" }
                ]
            }
        """.trimIndent()
    )

    val EBSIVectorExampleDeferredIssuance = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials":
                [
                    { "format": "jwt_vc", "type": "DeferredIssuance" }
                ]
            }
        """.trimIndent()
    )

    val EBSIVectorExamplePreAuthIssuance = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials":
                [
                    { "format": "jwt_vc", "type": "PreAuthIssuance" }
                ]
            }
        """.trimIndent()
    )

    val EBSIVectorExampleAllIssuance = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials":
                [
                    { "format": "jwt_vc", "type": "InTimeIssuance" },
                    { "format": "jwt_vc", "type": "DeferredIssuance" },
                    { "format": "jwt_vc", "type": "PreAuthIssuance" }

                ]
            }
        """.trimIndent()
    )
    //language=json
    val dynamicPolicy = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials":
                [
                    {
                        "format": "jwt_vc_json",
                        "type": "OpenBadgeCredential",
                        "policies":
                        [
                            $dynamiPolicy
                        ]
                    }
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
                "vc_policies": ${vcPoliciesData("\"revoked-status-list\"")},
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
                "vc_policies": ${vcPoliciesData("\"revoked-status-list\"")},
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
                "vc_policies": ${vcPoliciesData("\"revoked-status-list\"")},
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
                                            "${'$'}.vc.type"
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
                "vc_policies": ${vcPoliciesData("\"revoked-status-list\"")},
                "request_credentials":
                [
                    {
                     "format": "jwt_vc",
                     "input_descriptor": {
                            "id": "e3d700aa-0988-4eb6-b9c9-e00f4b27f1d8",
                            "constraints":
                            {
                                "fields":
                                [
                                    {
                                        "path":
                                        [
                                            "${'$'}.vc.type"
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
                ]
            }
        """.trimIndent()
    )

    val lspPotentialMdocExample = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "request_credentials": [ { "format": "mso_mdoc", "doc_type": "org.iso.18013.5.1.mDL" } ],
                "trustedRootCAs":[ ${Json.encodeToJsonElement("-----BEGIN CERTIFICATE-----\nMIIBZTCCAQugAwIBAgII2x50/ui7K2wwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMTURPQyBST09UIENBMCAXDTI1MDUxNDE0MDI1M1oYDzIwNzUwNTAyMTQwMjUzWjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARY/Swb4KSMi1n0p8zewsX6ssZvwdgJ+eWwgf81YmOJeRPHnuvIMth9NTpBdi6RUodKrowR5u9A+pMlPVuVn/F4oz8wPTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUxaGwGuK+ZbdzYNqADTyJ/gqLRwkwCgYIKoZIzj0EAwIDSAAwRQIhAOEYhbDYF/1kgDgy4anwZfoULmwt4vt08U6EU2AjXI09AiACCM7m3FnO7bc+xYQRT+WBkZXe/Om4bVmlIK+av+SkCA==\n-----END CERTIFICATE-----\n")} ],
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
            {"request_credentials":[{"format":"vc+sd-jwt","vct":"https://issuer.portal.walt-test.cloud/identity_credential","input_descriptor":{"id":"https://issuer.portal.walt-test.cloud/identity_credential","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["${'$'}.vct"],"filter":{"type":"string","pattern":"https://issuer.portal.walt-test.cloud/identity_credential"}},{"path":["${'$'}.birthdate"],"filter":{"type":"string","pattern":".*"}}],"limit_disclosure":"required"}}}],"vp_policies":["signature_sd-jwt-vc","presentation-definition"],"vc_policies":["not-before","expired"]}
        """.trimIndent()
    )
}

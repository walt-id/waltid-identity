package id.walt.verifier.openapi

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.verifier.openapi.VerifierApiExamples.ietfStatusPolicy
import id.walt.verifier.openapi.VerifierApiExamples.w3cListStatusPolicy
import id.walt.verifier.openapi.VerifierApiExamples.w3cStatusPolicy
import io.github.smiley4.ktoropenapi.config.SimpleBodyConfig
import io.github.smiley4.ktoropenapi.config.ValueExampleDescriptorConfig
import kotlinx.serialization.json.*

object VerifierApiExamples {

    //todo: remove line when ktor-swagger-ui#107 is fixed
    private fun jsonObjectValueExampleDescriptorDsl(content: String): ValueExampleDescriptorConfig<JsonObject>.() -> Unit = {
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
    private val vpRequiredCredentialsData = """
        [
            {
                "policy": "vp_required_credentials",
                "args": {
                    "required": [
                        { "credential_type": "gx:Issuer"},
                        { "credential_type": "gx:LegalPerson" },
                        { "any_of": [ "gx:EORI", "gx:LeiCode", "gx:VatID" ] }
                    ]
                }
            }
        ]
    """.trimIndent()

    // language=json
    private fun vcPoliciesData(additional: String? = null) = let {
        """
        [
            "signature",
            "expired",
            "not-before"
            ${additional?.let { ",$it" } ?: ""}
        ]
    """.trimIndent()
    }//${additional.joinToString { "$it" }}

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

    //language=json
    val vpRequiredCredentialsLogic = jsonObjectValueExampleDescriptorDsl(
        """
            {
                "vp_policies": $vpRequiredCredentialsData,
                "request_credentials":
                [
                    { "format": "jwt_vc_json", "type": "gx:Issuer" },
                    { "format": "jwt_vc_json", "type": "gx:LegalPerson" },
                    { "format": "jwt_vc_json", "type": "gx:EORI" },
                    { "format": "jwt_vc_json", "type": "gx:LeiCode" },
                    { "format": "jwt_vc_json", "type": "gx:VatID" }
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
                "trusted_root_cas":[ ${Json.encodeToJsonElement("-----BEGIN CERTIFICATE-----\nMIIBZTCCAQugAwIBAgII2x50/ui7K2wwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMTURPQyBST09UIENBMCAXDTI1MDUxNDE0MDI1M1oYDzIwNzUwNTAyMTQwMjUzWjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARY/Swb4KSMi1n0p8zewsX6ssZvwdgJ+eWwgf81YmOJeRPHnuvIMth9NTpBdi6RUodKrowR5u9A+pMlPVuVn/F4oz8wPTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUxaGwGuK+ZbdzYNqADTyJ/gqLRwkwCgYIKoZIzj0EAwIDSAAwRQIhAOEYhbDYF/1kgDgy4anwZfoULmwt4vt08U6EU2AjXI09AiACCM7m3FnO7bc+xYQRT+WBkZXe/Om4bVmlIK+av+SkCA==\n-----END CERTIFICATE-----\n")} ],
                "openid_profile": "ISO_18013_7_MDOC"
            }

        """.trimIndent()
    )

    val iacaRootCertificate =
        "-----BEGIN CERTIFICATE-----\nMIIBtDCCAVmgAwIBAgIUAOXLkeu9penFRno6oDcOBgT1odYwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDYzOTQ0WhcNNDAwNTI5MDYzOTQ0WjAoMQswCQYDVQQGEwJBVDEZMBcGA1UEAwwQV2FsdGlkIFRlc3QgSUFDQTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABAZGrRN7Oeanhn7MOaGU6HhaCt8ZMySk/nRHefLbRq8lChr+PS6JqpCJ503sEvByXzPDgPsp0urKg/y0E+F7q9+jYTBfMB0GA1UdDgQWBBTxCn2nWMrE70qXb614U14BweY2azASBgNVHRMBAf8ECDAGAQH/AgEAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDSQAwRgIhAOM37BjC48KhsSlU6mdJwlTLrad9VzlXVKc1GmjoCNm1AiEAkFRJalpz62QCOby9l7Vkq0LAdWVKiFMd0DmSxjsdT2U=\n-----END CERTIFICATE-----\n"

    val mDLRequiredFieldsExample = buildJsonObject {
        put("request_credentials", buildJsonArray {
            add(buildJsonObject {
                put("id", "mDL-request".toJsonElement())
                put("input_descriptor", buildJsonObject {
                    put("id", "org.iso.18013.5.1.mDL".toJsonElement())
                    put("format", buildJsonObject {
                        put("mso_mdoc", buildJsonObject {
                            put("alg", buildJsonArray {
                                add("ES256")
                            })
                        })
                    })
                    put("constraints", buildJsonObject {
                        put("fields", buildJsonArray {
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['family_name']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['given_name']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['birth_date']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['issue_date']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['expiry_date']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['issuing_country']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['issuing_authority']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['document_number']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['portrait']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['driving_privileges']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['un_distinguishing_sign']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                        })
                        put("limit_disclosure", "required".toJsonElement())
                    })
                })
            })
        })
        put("trusted_root_cas", buildJsonArray {
            add(iacaRootCertificate.toJsonElement())
        })
        put("openid_profile", OpenId4VPProfile.ISO_18013_7_MDOC.toString().toJsonElement())
    }

    val mDLBirthDateSelectiveDisclosureExample = buildJsonObject {
        put("request_credentials", buildJsonArray {
            add(buildJsonObject {
                put("id", "mDL-request".toJsonElement())
                put("input_descriptor", buildJsonObject {
                    put("id", "org.iso.18013.5.1.mDL".toJsonElement())
                    put("format", buildJsonObject {
                        put("mso_mdoc", buildJsonObject {
                            put("alg", buildJsonArray {
                                add("ES256")
                            })
                        })
                    })
                    put("constraints", buildJsonObject {
                        put("fields", buildJsonArray {
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['birth_date']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['issue_date']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['expiry_date']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                        })
                        put("limit_disclosure", "required".toJsonElement())
                    })
                })
            })
        })
        put("trusted_root_cas", buildJsonArray {
            add(iacaRootCertificate.toJsonElement())
        })
        put("openid_profile", OpenId4VPProfile.ISO_18013_7_MDOC.toString().toJsonElement())
    }

    val mDLAgeOver18AttestationExample = buildJsonObject {
        put("request_credentials", buildJsonArray {
            add(buildJsonObject {
                put("id", "mDL-request".toJsonElement())
                put("input_descriptor", buildJsonObject {
                    put("id", "org.iso.18013.5.1.mDL".toJsonElement())
                    put("format", buildJsonObject {
                        put("mso_mdoc", buildJsonObject {
                            put("alg", buildJsonArray {
                                add("ES256")
                            })
                        })
                    })
                    put("constraints", buildJsonObject {
                        put("fields", buildJsonArray {
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['age_over_18']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['issue_date']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                            add(buildJsonObject {
                                put("path", buildJsonArray {
                                    add("$['org.iso.18013.5.1']['expiry_date']")
                                })
                                put("intent_to_retain", false.toJsonElement())
                            })
                        })
                        put("limit_disclosure", "required".toJsonElement())
                    })
                })
            })
        })
        put("trusted_root_cas", buildJsonArray {
            add(iacaRootCertificate.toJsonElement())
        })
        put("openid_profile", OpenId4VPProfile.ISO_18013_7_MDOC.toString().toJsonElement())
    }

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

    fun w3cStatusPolicy(type: String = "BitstringStatusList", purpose: String = "revocation", value: UInt = 0u) =
        jsonObjectValueExampleDescriptorDsl(
            """
            {
                "request_credentials":
                [
                    { "format": "jwt_vc_json", "type": "OpenBadgeCredential" }
                ],
                "policy": "credential-status",
                "args":  ${w3cStatusPolicyArgument(purpose, type, value)}
            }
            """.trimIndent()
        )

    private fun w3cStatusPolicyArgument(purpose: String, type: String, value: UInt): String =
        """
            {
                "discriminator": "w3c",
                "value": $value,
                "purpose": "$purpose",
                "type": "$type"
            }
        """.trimIndent()

    val w3cListStatusPolicy = jsonObjectValueExampleDescriptorDsl(
        """
        {
            "request_credentials":
            [
                { "format": "jwt_vc_json", "type": "OpenBadgeCredential" }
            ],
            "policy": "credential-status",
            "args": {
                "discriminator": "w3c-list",
                "list": [
                    ${w3cStatusPolicyArgument("revocation", "BitstringStatusList", 0u)},
                    ${w3cStatusPolicyArgument("suspension", "BitstringStatusList", 0u)}
                ]
            }
        }
    """.trimIndent()
    )

    val ietfStatusPolicy = jsonObjectValueExampleDescriptorDsl(
        """
        {
            "request_credentials":
            [
                { "format": "vc+sd-jwt", "vct": "http://localhost:7002/identity_credential" }
            ],
            "policy": "credential-status",
            "args":  {
                "discriminator": "ietf",
                "value": 0
            }
        }
    """.trimIndent()
    )
}

fun SimpleBodyConfig.addCredentialStatusExamples() {
    example("Credential status - BitstringStatusList", w3cStatusPolicy("BitstringStatusList", "revocation"))
    example(
        "Credential status - BitstringStatusList custom valid value",
        w3cStatusPolicy("BitstringStatusList", "custom", 2u)
    )
    example("Credential status - StatusList2021", w3cStatusPolicy("StatusList2021", "revocation"))
    example("Credential status - RevocationList2020", w3cStatusPolicy("RevocationList2020", "revocation"))
    example("Credential status - multiple statuses", w3cListStatusPolicy)
    example("Credential status - TokenStatusList", ietfStatusPolicy)
}


fun SimpleBodyConfig.addSdJwtVcExamples() {
    example("SD-JWT-VC verification example", VerifierApiExamples.lspPotentialSDJwtVCExample)
    example(
        "SD-JWT-VC verification example with mandatory fields", VerifierApiExamples.sdJwtVCExampleWithRequiredFields
    )
}

fun SimpleBodyConfig.addEbsiVectorInteropTestExamples() {
    example(
        "EBSI-VECTOR interoperability test - InTimeIssuance", VerifierApiExamples.EBSIVectorExampleInTimeIssuance
    )
    example(
        "EBSI-VECTOR interoperability test - DeferredIssuance",
        VerifierApiExamples.EBSIVectorExampleDeferredIssuance
    )
    example(
        "EBSI-VECTOR interoperability test - PreAuthIssuance", VerifierApiExamples.EBSIVectorExamplePreAuthIssuance
    )
    example("EBSI-VECTOR interoperability test - All", VerifierApiExamples.EBSIVectorExampleAllIssuance)
}

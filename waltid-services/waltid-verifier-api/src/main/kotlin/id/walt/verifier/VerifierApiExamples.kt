package id.walt.verifier

import io.github.smiley4.ktorswaggerui.dsl.routes.ValueExampleDescriptorDsl

object VerifierApiExamples {

    //todo: remove line when ktor-swagger-ui#107 is fixed
    private fun jsonObjectValueExampleDescriptorDsl(content: String): ValueExampleDescriptorDsl.() -> Unit = {
        value = content
    }

    // Minimal call, default policies will be used, PresentationDefinition is generated based on credentials requested in `request_credentials`:
    //language=json
    val minimal = jsonObjectValueExampleDescriptorDsl(
        """
{
  "request_credentials": [
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
  "vp_policies": [
    { "policy": "minimum-credentials", "args": 2 },
    { "policy": "maximum-credentials", "args": 100 }
  ],
  "request_credentials": [
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
  "vp_policies": [
    { "policy": "minimum-credentials", "args": 2 },
    { "policy": "maximum-credentials", "args": 100 }
  ],
  "vc_policies": [
    "signature",
    "revoked",
    "expired",
    "not-before"
  ],
  "request_credentials": [
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
  "vp_policies": [
    { "policy": "minimum-credentials", "args": 2 },
    { "policy": "maximum-credentials", "args": 100 }
  ],
  "vc_policies": [
    "signature",
    "revoked",
    "expired",
    "not-before"
  ],
  "request_credentials": [
    "VerifiableId",
    "ProofOfResidence",
    {
      "credential": "OpenBadgeCredential",
      "policies": [
        "signature",
        {
          "policy": "schema",
          "args": {
            "type": "object",
            "required": [ "issuer" ],
            "properties": {
              "issuer": {
                "type": "object"
              }
            }
          }
        }
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
  "vp_policies": [
    { "policy": "minimum-credentials", "args": 2 },
    { "policy": "maximum-credentials", "args": 100 }
  ],
  "vc_policies": [
    "signature",
    "revoked",
    "expired",
    "not-before"
  ],
  "request_credentials": [
    "VerifiableId",
    "ProofOfResidence",
    {
      "credential": "OpenBadgeCredential",
      "policies": [
        "signature",
        {
          "policy": "schema",
          "args": {
            "type": "object",
            "required": [
              "issuer"
            ],
            "properties": {
              "issuer": {
                "type": "object"
              }
            }
          }
        }
      ]
    }
  ],
  "presentation_definition": {
    "id": "<automatically assigned>",
    "input_descriptors": [
      {
        "id": "VerifiableId",
        "format": {
          "jwt_vc_json": {
            "alg": [
              "EdDSA"
            ]
          }
        },
        "constraints": {
          "fields": [
            {
              "path": [
                "${'$'}.type"
              ],
              "filter": {
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
  "vp_policies": [
    "signature",
    "expired",
    "not-before",
    "presentation-definition"
  ],
  "vc_policies": [
    "signature",
    "expired",
    "not-before"
  ],
  "request_credentials": [
    "ProofOfResidence",
    {
      "credential": "OpenBadgeCredential",
      "policies": [
        "signature",
        {
          "policy": "schema",
          "args": {
            "type": "object",
            "required": [
              "issuer"
            ],
            "properties": {
              "issuer": {
                "type": "object"
              }
            }
          }
        }
      ]
    }
  ]
}
""".trimIndent()
    )

    val EbsiVerifiablePDA1 = jsonObjectValueExampleDescriptorDsl(
        """
    {
      "vc_policies": [
        "signature",
        "revoked_status_list",
        "expired",
        "not-before"
      ],
      "request_credentials": [
        "VerifiablePortableDocumentA1"
      ],
      "presentation_definition": {
        "id": "70fc7fab-89c0-4838-ba77-4886f47c3761",
        "input_descriptors": [
          {
            "id": "e3d700aa-0988-4eb6-b9c9-e00f4b27f1d8",
            "constraints": {
              "fields": [
                {
                  "path": [
                    "${'$'}.type"
                  ],
                  "filter": {
                    "contains": {
                      "const": "VerifiablePortableDocumentA1"
                    },
                    "type": "array"
                  }
                }
              ]
            }
          }
        ],
        "format": {
          "jwt_vc": {
            "alg": [
              "ES256"
            ]
          },
          "jwt_vp": {
            "alg": [
              "ES256"
            ]
          }
        }
      }
    }
    """.trimIndent()
    )
}

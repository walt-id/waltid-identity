package id.walt.openid4vp.verifier.openapi

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionSetup
import kotlinx.serialization.json.Json

object Verifier2OpenApiExamples {

    val basicExample = VerificationSessionSetup(
        null, DcqlQuery(
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

    fun exampleOf(s: String) = Json.decodeFromString<VerificationSessionSetup>(s)

    // language="JSON"
    val w3cPlusPath = """
        {
          "dcql_query": {
            "credentials": [
              {
                "id": "example_openbadge_jwt_vc",
                "format": "jwt_vc_json",
                "meta": {
                  "type_values": [
                    [
                      "VerifiableCredential",
                      "OpenBadgeCredential"
                    ]
                  ]
                },
                "claims": [
                  {
                    "path": [
                      "name"
                    ]
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    // language="JSON"
    val emptyMeta = """
        {
          "dcql_query": {
            "credentials": [
              {
                "id": "example_openbadge_jwt_vc",
                "format": "jwt_vc_json",
                "meta": { },
                "claims": [
                  {
                    "path": [
                      "name"
                    ]
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    // language="JSON"
    val nestedPresentationRequestW3C = """
         {
           "dcql_query": {
             "credentials": [
               {
                 "id": "example_openbadge_jwt_vc",
                 "format": "jwt_vc_json",
                 "meta": {
                   "type_values": [
                     [
                       "VerifiableCredential",
                       "OpenBadgeCredential"
                     ]
                   ]
                 },
                 "claims": [
                   {
                     "path": [
                       "credentialSubject",
                       "achievement",
                       "description"
                     ]
                   }
                 ]
               }
             ]
           }
         }
    """.trimIndent()

    // language="JSON"
    val nestedPresentationRequestWithMultipleClaims = """
    {
      "dcql_query": {
        "credentials": [
          {
            "id": "example_openbadge_jwt_vc",
            "format": "jwt_vc_json",
            "meta": {
              "type_values": [
                [
                  "VerifiableCredential",
                  "OpenBadgeCredential"
                ]
              ]
            },
            "claims": [
              {
                "path": [
                  "credentialSubject",
                  "achievement",
                  "description"
                ]
              },
              {
                "path": [
                  "credentialSubject",
                  "achievement",
                  "criteria",
                  "type"
                ]
              },
              {
                "path": [
                  "name"
                ]
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

    // language="JSON"
    val w3cTypeValues = """
         {
           "dcql_query": {
             "credentials": [
               {
                 "id": "example_openbadge_jwt_vc",
                 "format": "jwt_vc_json",
                 "meta": {
                   "type_values": [
                     [
                       "OpenBadgeCredential"
                     ]
                   ]
                 },
                 "claims": [
                   {
                     "path": [
                       "name"
                     ]
                   }
                 ]
               }
             ]
           }
         }
    """.trimIndent()

    // language="JSON"
    val W3CWithoutClaims = """
    {
      "dcql_query": {
        "credentials": [
          {
            "id": "example_openbadge_jwt_vc",
            "format": "jwt_vc_json",
            "meta": {
              "type_values": [
                [
                  "OpenBadgeCredential"
                ]
              ]
            }
          }
        ]
      }
    }
    """.trimIndent()


    // language="JSON"
    val W3CWithClaimsAndValues = """
    {
      "dcql_query": {
        "credentials": [
          {
            "id": "example_openbadge_jwt_vc",
            "format": "jwt_vc_json",
            "meta": {
              "type_values": [
                [
                  "VerifiableCredential",
                  "OpenBadgeCredential"
                ]
              ]
            },
            "claims": [
              {
                "path": [
                  "name"
                ],
                "values": [
                  "JFF x vc-edu PlugFest 3 Interoperability"
                ]
              }
            ]
          }
        ]
      }
    }
            """.trimIndent()

}

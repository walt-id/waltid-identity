package id.walt.webwallet.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.intellij.lang.annotations.Language

object IssuanceExamples {
    //language=json
    val universityDegreeCredential = """
{
  "issuerKey": {
    "type": "jwk",
    "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
  },
  "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
  "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
  "credentialData": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://www.w3.org/2018/credentials/examples/v1"
    ],
    "id": "http://example.gov/credentials/3732",
    "type": [
      "VerifiableCredential",
      "UniversityDegreeCredential"
    ],
    "issuer": {
      "id": "did:web:vc.transmute.world"
    },
    "issuanceDate": "2020-03-10T04:24:12.164Z",
    "credentialSubject": {
      "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
      "degree": {
        "type": "BachelorDegree",
        "name": "Bachelor of Science and Arts"
      }
    }
  },
  "mapping": {
    "id": "\u003cuuid\u003e",
    "issuer": {
      "id": "\u003cissuerDid\u003e"
    },
    "credentialSubject": {
      "id": "\u003csubjectDid\u003e"
    },
    "issuanceDate": "\u003ctimestamp\u003e",
    "expirationDate": "\u003ctimestamp-in:365d\u003e"
  }
}
""".trimIndent()

    //language=json
    val openBadgeCredentialExampleJsonString = """
{
  "issuerKey": {
    "type": "jwk",
    "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
  },
  "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
  "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
  "credentialData": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
    ],
    "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
    "type": [
      "VerifiableCredential",
      "OpenBadgeCredential"
    ],
    "name": "JFF x vc-edu PlugFest 3 Interoperability",
    "issuer": {
      "type": [
        "Profile"
      ],
      "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
      "name": "Jobs for the Future (JFF)",
      "url": "https://www.jff.org/",
      "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
    },
    "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
    "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
    "credentialSubject": {
      "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
      "type": [
        "AchievementSubject"
      ],
      "achievement": {
        "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
        "type": [
          "Achievement"
        ],
        "name": "JFF x vc-edu PlugFest 3 Interoperability",
        "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
        "criteria": {
          "type": "Criteria",
          "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
        },
        "image": {
          "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
          "type": "Image"
        }
      }
    }
  },
  "mapping": {
    "id": "\u003cuuid\u003e",
    "issuer": {
      "id": "\u003cissuerDid\u003e"
    },
    "credentialSubject": {
      "id": "\u003csubjectDid\u003e"
    },
    "issuanceDate": "\u003ctimestamp\u003e",
    "expirationDate": "\u003ctimestamp-in:365d\u003e"
  }
}
""".trimIndent()
    val openBadgeCredentialExample = Json.parseToJsonElement(openBadgeCredentialExampleJsonString).jsonObject.toMap()

    @Language("JSON")
    val testCredential =
        """
            {
              "issuerKey": {
                "type": "jwk",
                "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
              },
              "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
              "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
              "credentialData": {
                "@context": [
                  "https://www.w3.org/2018/credentials/v1",
                  "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                ],
                "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
                "type": [
                  "VerifiableCredential",
                  "OpenBadgeCredential"
                ],
                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                "issuer": {
                  "type": [
                    "Profile"
                  ],
                  "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
                  "name": "Jobs for the Future (JFF)",
                  "url": "https://www.jff.org/",
                  "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                },
                "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
                "credentialSubject": {
                  "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                  "type": [
                    "AchievementSubject"
                  ],
                  "achievement": {
                    "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                    "type": [
                      "Achievement"
                    ],
                    "name": "JFF x vc-edu PlugFest 3 Interoperability",
                    "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                    "criteria": {
                      "type": "Criteria",
                      "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                    },
                    "image": {
                      "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                      "type": "Image"
                    }
                  }
                }
              },
              "mapping": {
                "id": "<uuid>",
                "issuer": {
                  "id": "<issuerDid>"
                },
                "credentialSubject": {
                  "id": "<subjectDid>"
                },
                "issuanceDate": "<timestamp>",
                "expirationDate": "<timestamp-in:365d>"
              }
            }
            """.trimIndent()

    val universityDegreeCredentialExample2 = mapOf(
        "@context" to listOf(
            "https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"
        ),
        "id" to "http://example.gov/credentials/3732",
        "type" to listOf(
            "VerifiableCredential", "UniversityDegreeCredential"
        ),
        "issuer" to mapOf(
            "id" to "did:web:vc.transmute.world"
        ),
        "issuanceDate" to "2020-03-10T04:24:12.164Z",
        "credentialSubject" to mapOf(
            "id" to "did:example:ebfeb1f712ebc6f1c276e12ec21", "degree" to mapOf(
                "type" to "BachelorDegree",
                "name" to "Bachelor of Science and Arts"
            )
        ),
    )

    val universityDegreeCredentialSignedExample = universityDegreeCredentialExample2.plus(
        mapOf(
            "proof" to mapOf(
                "type" to "JsonWebSignature2020",
                "created" to "2020-03-21T17:51:48Z",
                "verificationMethod" to "did:web:vc.transmute.world#_Qq0UL2Fq651Q0Fjd6TvnYE-faHiOpRlPVQcY_-tA4A",
                "proofPurpose" to "assertionMethod",
                "jws" to "eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJFZERTQSJ9..OPxskX37SK0FhmYygDk-S4csY_gNhCUgSOAaXFXDTZx86CmI5nU9xkqtLWg-f4cqkigKDdMVdtIqWAvaYx2JBA"
            )
        )
    )

    //language=JSON
    val batchExample = """
        [
          {
            "issuerKey": {
              "type": "jwk",
              "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
            },
            "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
            "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
            "credentialData": {
              "@context": [
                "https://www.w3.org/2018/credentials/v1",
                "https://www.w3.org/2018/credentials/examples/v1"
              ],
              "id": "http://example.gov/credentials/3732",
              "type": [
                "VerifiableCredential",
                "UniversityDegreeCredential"
              ],
              "issuer": {
                "id": "did:web:vc.transmute.world"
              },
              "issuanceDate": "2020-03-10T04:24:12.164Z",
              "credentialSubject": {
                "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
                "degree": {
                  "type": "BachelorDegree",
                  "name": "Bachelor of Science and Arts"
                }
              }
            },
            "mapping": {
              "id": "<uuid>",
              "issuer": {
                "id": "<issuerDid>"
              },
              "credentialSubject": {
                "id": "<subjectDid>"
              },
              "issuanceDate": "<timestamp>",
              "expirationDate": "<timestamp-in:365d>"
            }
          },
          {
            "issuerKey": {
              "type": "jwk",
              "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
            },
            "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
            "credentialData": {
              "@context": [
                "https://www.w3.org/2018/credentials/v1",
                "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
              ],
              "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
              "type": [
                "VerifiableCredential",
                "OpenBadgeCredential"
              ],
              "name": "JFF x vc-edu PlugFest 3 Interoperability",
              "issuer": {
                "type": [
                  "Profile"
                ],
                "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
                "name": "Jobs for the Future (JFF)",
                "url": "https://www.jff.org/",
                "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
              },
              "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
              "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
              "credentialSubject": {
                "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                "type": [
                  "AchievementSubject"
                ],
                "achievement": {
                  "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                  "type": [
                    "Achievement"
                  ],
                  "name": "JFF x vc-edu PlugFest 3 Interoperability",
                  "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                  "criteria": {
                    "type": "Criteria",
                    "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                  },
                  "image": {
                    "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                    "type": "Image"
                  }
                }
              }
            },
            "mapping": {
              "id": "<uuid>",
              "issuer": {
                "id": "<issuerDid>"
              },
              "credentialSubject": {
                "id": "<subjectDid>"
              },
              "issuanceDate": "<timestamp>",
              "expirationDate": "<timestamp-in:365d>"
            }
          }
        ]
    """.trimIndent()

    //language=JSON
    val sdJwtExample = """
        {
          "issuerKey": {
            "type": "jwk",
            "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
          },
          "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
          "credentialData": {
            "@context": [
              "https://www.w3.org/2018/credentials/v1",
              "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
            ],
            "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
            "type": [
              "VerifiableCredential",
              "OpenBadgeCredential"
            ],
            "name": "JFF x vc-edu PlugFest 3 Interoperability",
            "issuer": {
              "type": [
                "Profile"
              ],
              "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
              "name": "Jobs for the Future (JFF)",
              "url": "https://www.jff.org/",
              "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
            },
            "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
            "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
            "credentialSubject": {
              "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
              "type": [
                "AchievementSubject"
              ],
              "achievement": {
                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                "type": [
                  "Achievement"
                ],
                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                "criteria": {
                  "type": "Criteria",
                  "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                },
                "image": {
                  "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                  "type": "Image"
                }
              }
            }
          },
          "mapping": {
            "id": "<uuid>",
            "issuer": {
              "id": "<issuerDid>"
            },
            "credentialSubject": {
              "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp>",
            "expirationDate": "<timestamp-in:365d>"
          },
          "selectiveDisclosure": {
            "fields": {"name": {"sd": true}}
          }
        }
    """.trimIndent()
}

package id.walt.issuer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object IssuanceExamples {
    // language=json
    val universityDegreeCredential = """
{
  "issuerKey": {
    "type": "jwk",
    "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
  },
  "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
  "vc": {
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

    // language=json
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
                "type" to "BachelorDegree", "name" to "Bachelor of Science and Arts"
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

    // language=JSON
    val batchExample = """
        [
          {
            "issuerKey": {
              "type": "jwk",
              "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
            },
            "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
            "vc": {
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
            "vc": {
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

    // language=JSON
    val sdJwtExample = """
        {
          "issuerKey": {
            "type": "jwk",
            "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
          },
          "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
          "credentialConfigurationId": "OpenBadgeCredential_vc+sd-jwt",
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

    // language=JSON
    val issuerOnboardingRequestDefaultExample = """
        {
          "key": {
            "backend": "jwk",
            "keyType": "secp256r1"
          },
          "did": {
            "method" : "jwk"
          }
        }
    """.trimIndent()

    // language=JSON
    val issuerOnboardingRequestTseExample = """
        {
          "key": {
            "backend": "tse",
            "keyType": "Ed25519",
            "config": {
              "server": "http://127.0.0.1:8200/v1/transit",
              "accessKey": "dev-only-token"
            }
          },
          "did": {
            "method": "key"
          }
        }
    """.trimIndent()

    //language=JSON
    val issuerOnboardingRequestOciExample = """
        {
          "key": {
            "backend": "oci",
            "keyType": "secp256r1",
            "config": {
              "vaultId" : "ocid1.vault.oc1.eu-frankfurt-1.enta2fneaadmk",
              "compartmentId": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
            }
          },
          "did": {
            "method": "jwk"
          }
        }
    """.trimIndent()

    //language=JSON
    val issuerOnboardingResponseOciExample = """
        {
          "key": {
            "type": "oci",
            "config": {
              "vaultId" : "ocid1.vault.oc1.eu-frankfurt-1.enta2fneaadmk",
              "compartmentId": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
            },
            "id": "ocid1.key.oc1.eu-frankfurt-1.enta2fneaadmk.abtheljrlj5snthwkx7ycdmknuftght527dkyjsoz72dcogklixrsdyolo5a",
            "_publicKey": "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"tT1DAZdtp7vUPphTxoilmr6dfZPKcPfwL8G_Ri3K0_E\",\"y\":\"JabPubkHQPK0G7O8eL3bKg75hX4Wkojb_AOepX8xdAs\"}",
            "_keyType": "secp256r1"
          },
          "did": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6InRUMURBWmR0cDd2VVBwaFR4b2lsbXI2ZGZaUEtjUGZ3TDhHX1JpM0swX0UiLCJ5IjoiSmFiUHVia0hRUEswRzdPOGVMM2JLZzc1aFg0V2tvamJfQU9lcFg4eGRBcyJ9"
        }

    """.trimIndent()

    // language=JSON
    val issuerOnboardingRequestDidWebExample = """
        {
          "key": {
            "backend": "jwk",
            "keyType": "secp256k1"
          },
          "did": {
            "method": "web",
            "config": {
                "domain": "example.com",
                "path": "optional-user-id-1234"
            }
          }
        }
    """.trimIndent()

    // language=JSON
    val issuerOnboardingResponseDidWebExample = """
        {
   "issuerKey":{
      "type":"jwk",
      "jwk": {
              "kty": "EC",
              "d": "sMjI1SVu4vKHLr3JwgUMu10Ihn5OL0sCaqjfZP8xpUU",
              "crv": "secp256k1",
              "kid": "Si07jIXqLsMKHy0vgyvPbcIvIPxdqL7Qs6STqrx1UC8",
              "x": "q-LZDK-TZQSUczy_1K6TBFeVn60rMv4KjYvTePy2TGs",
              "y": "qTbiSREfWRZtAKZsW-k-0BHIIYpAN0fhnjaqeMIU5OY"
            }
   },
   "issuerDid":"did:web:example.com:optional-user-id-1234"
}
    """.trimIndent()

    // language=JSON
    val issuerOnboardingResponseDefaultExample = """
        {
          "key": {
            "type": "jwk",
            "jwk": {
              "kty": "EC",
              "d": "sMjI1SVu4vKHLr3JwgUMu10Ihn5OL0sCaqjfZP8xpUU",
              "crv": "P-256",
              "kid": "Si07jIXqLsMKHy0vgyvPbcIvIPxdqL7Qs6STqrx1UC8",
              "x": "q-LZDK-TZQSUczy_1K6TBFeVn60rMv4KjYvTePy2TGs",
              "y": "qTbiSREfWRZtAKZsW-k-0BHIIYpAN0fhnjaqeMIU5OY"
            }
          },
          "did": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiU2kwN2pJWHFMc01LSHkwdmd5dlBiY0l2SVB4ZHFMN1FzNlNUcXJ4MVVDOCIsIngiOiJxLUxaREstVFpRU1VjenlfMUs2VEJGZVZuNjByTXY0S2pZdlRlUHkyVEdzIiwieSI6InFUYmlTUkVmV1JadEFLWnNXLWstMEJISUlZcEFOMGZobmphcWVNSVU1T1kifQ"
        }
    """.trimIndent()

    // language=JSON
    val issuerOnboardingResponseTseExample = """
    {
      "key": {
        "type": "tse",
        "server": "http://127.0.0.1:8200/v1/transit",
        "accessKey": "dev-only-token",
        "id": "k208278175",
        "_publicKey": [
          -88,
          -85,
          -16,
          118,
          -63,
          124,
          73,
          86,
          16,
          70,
          -76,
          -92,
          3,
          60,
          98,
          -111,
          89,
          19,
          83,
          80,
          -10,
          94,
          -26,
          -116,
          69,
          26,
          -33,
          -50,
          49,
          -55,
          -117,
          22
        ],
        "_keyType": "Ed25519"
      },
      "did": "did:key:z6MkqogbukAXnhvY9dAtXw7ABpe9meJJRCYHwyrNA2q74o17"
    }
    """.trimIndent()
}

package id.walt.issuer.issuance

import id.walt.crypto.keys.KeyType
import io.github.smiley4.ktorswaggerui.dsl.routes.ValueExampleDescriptorDsl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object IssuanceExamples {
    // language=json
    private val openBadgeCredentialData = """
        {
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
          }
    """.trimIndent()
    // language=json
    private val universityDegreeCredentialData = """
        {
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
          }
    """.trimIndent()
    //language=json
    private val bankIdCredentialData = """
        {
             "@context":[
                "https://www.w3.org/2018/credentials/v1"
             ],
             "type":[
                "VerifiableCredential",
                "BankId"
             ],
             "credentialSubject":{
                "accountId":"1234567890",
                "IBAN":"DE99123456789012345678",
                "BIC":"DEUTDEDBBER",
                "birthDate":"1958-08-17",
                "familyName":"DOE",
                "givenName":"JOHN",
                "id":"identity#bankId"
             },
             "id":"identity#BankId#3add94f4-28ec-42a1-8704-4e4aa51006b4",
             "issued":"2021-08-31T00:00:00Z",
             "issuer":{
                "id":"did:key:z6MkrHKzgsahxBLyNAbLQyB1pcWNYC9GmywiWPgkrvntAZcj",
                "image":{
                   "id":"https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/1660296169313-K159K9WX8J8PPJE005HV/Walt+Bot_Logo.png?format=100w",
                   "type":"Image"
                },
                "name":"CH Authority",
                "type":"Profile",
                "url":"https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/1660296169313-K159K9WX8J8PPJE005HV/Walt+Bot_Logo.png?format=100w"
             },
             "validFrom":"2021-08-31T00:00:00Z",
             "issuanceDate":"2021-08-31T00:00:00Z"
          }
    """.trimIndent()
    //language=json
    private val issuerKey = """
        {
            "type": "jwk",
            "jwk": {
               "kty":"OKP",
               "d":"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI",
               "crv":"Ed25519",
               "kid":"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8",
               "x":"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM"
            }
          }
    """.trimIndent()
    private val issuerDid = "\"did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp\""
    //language=json
    private val mapping = """
        {
             "id":"<uuid>",
             "issuer":{
                "id":"<issuerDid>"
             },
             "credentialSubject":{
                "id":"<subjectDid>"
             },
             "issuanceDate":"<timestamp>",
             "expirationDate":"<timestamp-in:365d>"
          }
    """.trimIndent()

    // language=json
    private val openBadgeCredentialJson = """
        {
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
          "credentialData": $openBadgeCredentialData,
          "mapping": $mapping
        }
        """.trimIndent()
    //language=json
    private val universityDegreeCredentialJson = """
        {
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "credentialConfigurationId": "UniversityDegree_jwt_vc_json",
          "credentialData": $universityDegreeCredentialData,
          "mapping": $mapping
        }
        """.trimIndent()
    //language=json
    private val bankIdCredentialJson = """
        {
          "issuerKey":$issuerKey,
          "issuerDid":$issuerDid,
          "credentialConfigurationId":"BankId_jwt_vc_json",
          "credentialData":$bankIdCredentialData,
          "mapping":$mapping
       }
        """.trimIndent()

    val universityDegreeCredential: ValueExampleDescriptorDsl.() -> Unit = {
        value = universityDegreeCredentialJson
    }

    val openBadgeCredentialExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = openBadgeCredentialJson
    }

    val openBadgeCredentialExampleParsed = Json.parseToJsonElement(this.openBadgeCredentialJson).jsonObject.toMap()


    // language=json
    val openBadgeCredentialSignExampleJsonString: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
        {
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "subjectDid":"did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5Iiwia2lkIjoiMW1lTUJuX3EtVklTQzd5Yk42UnExX0FISkxwSHZKVG83N3V6Nk44UkdDQSIsIngiOiJQdEV1YlB1MWlrRzR5emZsYUF2dnNmTWIwOXR3NzlIcTFsVnJRX1c0ZnVjIn0",
          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
          "credentialData": $openBadgeCredentialData
        }
        """.trimIndent()
    }

    val universityDegreeCredentialSignedExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = mapOf(
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
            "proof" to mapOf(
                "type" to "JsonWebSignature2020",
                "created" to "2020-03-21T17:51:48Z",
                "verificationMethod" to "did:web:vc.transmute.world#_Qq0UL2Fq651Q0Fjd6TvnYE-faHiOpRlPVQcY_-tA4A",
                "proofPurpose" to "assertionMethod",
                "jws" to "eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJFZERTQSJ9..OPxskX37SK0FhmYygDk-S4csY_gNhCUgSOAaXFXDTZx86CmI5nU9xkqtLWg-f4cqkigKDdMVdtIqWAvaYx2JBA"
            )
        )
    }

    // language=JSON
    val batchExampleJwt: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
     [
       $openBadgeCredentialJson,
       $bankIdCredentialJson
    ]
    """.trimIndent()
    }


    // language=json
    val batchExampleSdJwt: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
        [
           {
              "issuerKey":$issuerKey,
              "issuerDid":$issuerDid,
              "credentialConfigurationId":"OpenBadgeCredential_vc+sd-jwt",
              "credentialData":$openBadgeCredentialData,
              "mapping":$mapping,
              "selectiveDisclosure":{
                 "fields":{
                    "name":{
                       "sd":true
                    }
                 }
              }
           },
           {
              "issuerKey":{
                 "type":"jwk",
                 "jwk":"{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
              },
              "issuerDid":"did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
              "credentialConfigurationId":"BankId_vc+sd-jwt",
              "credentialData":$bankIdCredentialData,
              "mapping":$mapping,
              "selectiveDisclosure":{
                 "fields":{
                    "credentialSubject":{
                       
                          "sd":true
                      
                    }
                 }
              }
           }
        ]
    """.trimIndent()
    }

    // language=JSON
    val sdJwtExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
        {
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "credentialConfigurationId": "OpenBadgeCredential_vc+sd-jwt",
          "credentialData": $openBadgeCredentialData,
          "mapping": $mapping,
          "selectiveDisclosure": {
            "fields": {"name": {"sd": true}}
          }
        }
    """.trimIndent()
    }

    private fun onboardingRequestWithKey(key: KeyType) =
        Json { encodeDefaults = true }.encodeToString( // todo: remove line when ktor-swagger-ui#107 is fixed
            OnboardingRequest(
                key = mapOf(
                    "backend" to "jwk",
                    "keyType" to key.name
                ),
                did = mapOf(
                    "method" to "jwk"
                )
            )
        ) // todo: remove line when ktor-swagger-ui#107 is fixed

    val issuerOnboardingRequestDefaultEd25519Example: ValueExampleDescriptorDsl.() -> Unit = {
        value = onboardingRequestWithKey(KeyType.Ed25519)
    }

    val issuerOnboardingRequestDefaultSecp256r1Example: ValueExampleDescriptorDsl.() -> Unit = {
        value = onboardingRequestWithKey(KeyType.secp256r1)
    }

    val issuerOnboardingRequestDefaultSecp256k1Example: ValueExampleDescriptorDsl.() -> Unit = {
        value = onboardingRequestWithKey(KeyType.secp256k1)
    }

    val issuerOnboardingRequestDefaultRsaExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = onboardingRequestWithKey(KeyType.RSA)
    }

    // language=JSON
    val issuerOnboardingRequestTseExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
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
    }

    //language=JSON
    val issuerOnboardingRequestOciExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
        {
          "key": {
            "backend": "oci",
            "keyType": "secp256r1",
            "config": {
              "vaultId" : "ocid1.vault.oc1.eu-frankfurt-1.enta2fneaadmk",
              "compartmentId": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q"
            }
          },
          "did": {
            "method": "jwk"
          }
        }
    """.trimIndent()
    }

    //language=JSON
    val issuerOnboardingResponseOciExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
        {
          "issuerKey": {
            "type": "oci",
            "config": {
              "vaultId" : "ocid1.vault.oc1.eu-frankfurt-1.enta2fneaadmk",
              "compartmentId": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q"
            },
            "id": "ocid1.key.oc1.eu-frankfurt-1.enta2fneaadmk.abtheljrlj5snthwkx7ycdmknuftght527dkyjsoz72dcogklixrsdyolo5a",
            "_publicKey": "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"tT1DAZdtp7vUPphTxoilmr6dfZPKcPfwL8G_Ri3K0_E\",\"y\":\"JabPubkHQPK0G7O8eL3bKg75hX4Wkojb_AOepX8xdAs\"}",
            "_keyType": "secp256r1"
          },
          "issuerDid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6InRUMURBWmR0cDd2VVBwaFR4b2lsbXI2ZGZaUEtjUGZ3TDhHX1JpM0swX0UiLCJ5IjoiSmFiUHVia0hRUEswRzdPOGVMM2JLZzc1aFg0V2tvamJfQU9lcFg4eGRBcyJ9"
        }

    """.trimIndent()
    }


    //language=JSON
    val issuerOnboardingRequestOciRestApiExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
        {
          "key": {
            "backend": "oci-rest-api",
            "keyType": "secp256r1",
             "config": {
              "tenancyOcid": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
              "compartmentOcid": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
              "userOcid": "ocid1.user.oc1..aaaaaaaaxjkkfjqxdqk7ldfjrxjmacmbi7sci73rbfiwpioehikavpbtqx5q",
              "fingerprint": "bb:d4:4b:0c:c8:3a:49:15:7f:87:55:d5:2b:7e:dd:bc",
              "managementEndpoint": "entaftlvaaemy-management.kms.eu-frankfurt-1.oraclecloud.com",
              "cryptoEndpoint": "entaftlvaaemy-crypto.kms.eu-frankfurt-1.oraclecloud.com",
              "signingKeyPem": "-----BEGIN PRIVATE KEY-----\n\n-----END PRIVATE KEY-----\n"
            }
          },
          "did": {
            "method": "jwk"
          }
        }
    """.trimIndent()
    }

    //language=JSON
    val issuerOnboardingResponseOciRestApiExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
        {
          "issuerKey": {
            "type": "oci",
             "config": {
              "tenancyOcid": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
              "compartmentOcid": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
              "userOcid": "ocid1.user.oc1..aaaaaaaaxjkkfjqxdqk7ldfjrxjmacmbi7sci73rbfiwpioehikavpbtqx5q",
              "fingerprint": "bb:d4:4b:0c:c8:3a:49:15:7f:87:55:d5:2b:7e:dd:bc",
              "managementEndpoint": "entaftlvaaemy-management.kms.eu-frankfurt-1.oraclecloud.com",
              "cryptoEndpoint": "entaftlvaaemy-crypto.kms.eu-frankfurt-1.oraclecloud.com",
              "signingKeyPem": "-----BEGIN PRIVATE KEY-----\n\n-----END PRIVATE KEY-----\n"
            },
            "id": "ocid1.key.oc1.eu-frankfurt-1.enta2fneaadmk.abtheljrlj5snthwkx7ycdmknuftght527dkyjsoz72dcogklixrsdyolo5a",
            "_publicKey": "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"tT1DAZdtp7vUPphTxoilmr6dfZPKcPfwL8G_Ri3K0_E\",\"y\":\"JabPubkHQPK0G7O8eL3bKg75hX4Wkojb_AOepX8xdAs\"}",
            "_keyType": "secp256r1"
          },
          "issuerDid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6InRUMURBWmR0cDd2VVBwaFR4b2lsbXI2ZGZaUEtjUGZ3TDhHX1JpM0swX0UiLCJ5IjoiSmFiUHVia0hRUEswRzdPOGVMM2JLZzc1aFg0V2tvamJfQU9lcFg4eGRBcyJ9"
        }

    """.trimIndent()
    }

    // language=JSON
    val issuerOnboardingRequestDidWebExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
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
    }

    // language=JSON
    val issuerOnboardingResponseDidWebExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
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
    }

    // language=JSON
    val issuerOnboardingResponseDefaultExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
        {
          "issuerKey": {
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
          "issuerDid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiU2kwN2pJWHFMc01LSHkwdmd5dlBiY0l2SVB4ZHFMN1FzNlNUcXJ4MVVDOCIsIngiOiJxLUxaREstVFpRU1VjenlfMUs2VEJGZVZuNjByTXY0S2pZdlRlUHkyVEdzIiwieSI6InFUYmlTUkVmV1JadEFLWnNXLWstMEJISUlZcEFOMGZobmphcWVNSVU1T1kifQ"
        }
    """.trimIndent()
    }

    // language=JSON
    val issuerOnboardingResponseTseExample: ValueExampleDescriptorDsl.() -> Unit = {
        value = """
    {
      "issuerKey": {
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
      "issuerDid": "did:key:z6MkqogbukAXnhvY9dAtXw7ABpe9meJJRCYHwyrNA2q74o17"
    }
    """.trimIndent()
    }
}

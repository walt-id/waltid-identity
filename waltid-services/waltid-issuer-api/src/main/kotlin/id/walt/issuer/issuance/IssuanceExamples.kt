package id.walt.issuer.issuance

import id.walt.commons.interop.LspPotentialInterop
import id.walt.crypto.keys.KeyType
import id.walt.issuer.lspPotential.LspPotentialIssuanceInterop
import io.github.smiley4.ktorswaggerui.dsl.routes.ValueExampleDescriptorDsl
import kotlinx.serialization.json.*

object IssuanceExamples {

    private inline fun <reified T> typedValueExampleDescriptorDsl(content: String): ValueExampleDescriptorDsl.() -> Unit = {
        value = Json.decodeFromString<T>(content)
    }

    // language=json
    val openBadgeCredentialData = """
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
              "name": "Jobs for the Future (JFF)",
              "url": "https://www.jff.org/",
              "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
            },
            "credentialSubject": {
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
    private val universityDegreeCredentialProofData = """
        {
            "type": "JsonWebSignature2020",
            "created": "2020-03-21T17:51:48Z",
            "verificationMethod": "did:web:vc.transmute.world#_Qq0UL2Fq651Q0Fjd6TvnYE-faHiOpRlPVQcY_-tA4A",
            "proofPurpose": "assertionMethod",
            "jws": "eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJFZERTQSJ9..OPxskX37SK0FhmYygDk-S4csY_gNhCUgSOAaXFXDTZx86CmI5nU9xkqtLWg-f4cqkigKDdMVdtIqWAvaYx2JBA"
        }
    """.trimIndent()

    // language=json
    private fun universityDegreeCredentialData(withProof: Boolean = false) = """
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
            ${if (withProof) ", \"proofType\": $universityDegreeCredentialProofData" else ""}
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
             "issuer":{
                "image":{
                   "id":"https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/1660296169313-K159K9WX8J8PPJE005HV/Walt+Bot_Logo.png?format=100w",
                   "type":"Image"
                },
                "name":"CH Authority",
                "type":"Profile",
                "url":"https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/1660296169313-K159K9WX8J8PPJE005HV/Walt+Bot_Logo.png?format=100w"
             }
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
    private const val issuerDid = "\"did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp\""

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
    val openBadgeCredentialIssuance = """
        {
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
          "credentialData": $openBadgeCredentialData,
          "mdocData": null,
          "mapping": $mapping
        }
        """.trimIndent()

    private val openBadgeCredentialIssuanceIdToken = """
        {
          "authenticationMethod": "ID_TOKEN",
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
          "credentialData": $openBadgeCredentialData,
          "mdocData": null,
          "mapping": $mapping
        }
        """.trimIndent()

    private val openBadgeCredentialIssuanceVpToken = """
        {
          "authenticationMethod": "VP_TOKEN",
          "vpRequestValue": "NaturalPersonVerifiableID",
          "vpProfile": "EBSIV3",
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
          "credentialData": $openBadgeCredentialData,
          "mdocData": null,
          "mapping": $mapping
        }
        """.trimIndent()

    private val openBadgeCredentialIssuancePwd = """
        {
          "authenticationMethod": "PWD",
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
          "credentialData": $openBadgeCredentialData,
          "mdocData": null,
          "mapping": $mapping
        }
        """.trimIndent()


    //language=json
    private val universityDegreeCredentialIssuance = """
        {
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "credentialConfigurationId": "UniversityDegree_jwt_vc_json",
          "credentialData": ${universityDegreeCredentialData()},
          "mdocData": null,
          "mapping": $mapping
        }
        """.trimIndent()

    //language=json
    private val universityDegreeCredentialSignRequest = """
        {
          "issuerKey": $issuerKey,
          "issuerDid": $issuerDid,
          "subjectDid": $issuerDid,
          "credentialData": ${universityDegreeCredentialData(withProof = false)}
        }
    """.trimIndent()

    //language=text
    private val universityDegreeCredentialSignResponse = """
        "eyJhbGciOiJFZERTQSIsImtpZCI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIn0.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy9leGFtcGxlcy92MSJdLCJpZCI6Imh0dHA6Ly9leGFtcGxlLmdvdi9jcmVkZW50aWFscy8zNzMyIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWVDcmVkZW50aWFsIl0sImlzc3VlciI6eyJpZCI6ImRpZDp3ZWI6dmMudHJhbnNtdXRlLndvcmxkIn0sImlzc3VhbmNlRGF0ZSI6IjIwMjAtMDMtMTBUMDQ6MjQ6MTIuMTY0WiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmV4YW1wbGU6ZWJmZWIxZjcxMmViYzZmMWMyNzZlMTJlYzIxIiwiZGVncmVlIjp7InR5cGUiOiJCYWNoZWxvckRlZ3JlZSIsIm5hbWUiOiJCYWNoZWxvciBvZiBTY2llbmNlIGFuZCBBcnRzIn19fX0.lEprwxQtj4a7k64OxjJV4dGNkuf3Fjks48fU0keJshm-dKtzmHktC3H3rh6iOZTi78p4m5Uu5Y9QmsJJOd8jCg"
    """.trimIndent()

    //language=json
    private val bankIdCredentialIssuance = """
        {
          "issuerKey":$issuerKey,
          "issuerDid":$issuerDid,
          "credentialConfigurationId":"BankId_jwt_vc_json",
          "credentialData":$bankIdCredentialData,
          "mdocData": null,
          "mapping":$mapping
       }
        """.trimIndent()

    //language=json
    private fun onboardingRequestWithKey(key: KeyType) = """
        {
            "key":
            {
                "backend": "jwk",
                "keyType": "${key.name}"
            },
            "did":
            {
                "method": "jwk"
            }
        }
    """.trimIndent()

    val jwkKeyExample = typedValueExampleDescriptorDsl<String>(
        issuerKey
    )

    val universityDegreeIssuanceCredentialExample = typedValueExampleDescriptorDsl<IssuanceRequest>(
        universityDegreeCredentialIssuance
    )

    val universityDegreeSignRequestCredentialExample = typedValueExampleDescriptorDsl<JsonObject>(
        universityDegreeCredentialSignRequest
    )

    val universityDegreeSignResponseCredentialExample = typedValueExampleDescriptorDsl<String>(
        universityDegreeCredentialSignResponse
    )

    val openBadgeCredentialIssuanceExample = typedValueExampleDescriptorDsl<IssuanceRequest>(
        openBadgeCredentialIssuance
    )

    val openBadgeCredentialIssuanceExampleWithIdToken = typedValueExampleDescriptorDsl<IssuanceRequest>(
        openBadgeCredentialIssuanceIdToken
    )
    val openBadgeCredentialIssuanceExampleWithVpToken = typedValueExampleDescriptorDsl<IssuanceRequest>(
        openBadgeCredentialIssuanceVpToken
    )
    val openBadgeCredentialIssuanceExampleWithUsernamePassword = typedValueExampleDescriptorDsl<IssuanceRequest>(
        openBadgeCredentialIssuancePwd
    )

    // language=json
    val mDLCredentialIssuanceData = """
        {
          "issuerKey": { 
            "type": "jwk",
            "jwk": ${Json.parseToJsonElement(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_JWK_KEY.jwk!!)}
          },
          "issuerDid":"",
          "credentialConfigurationId":"org.iso.18013.5.1.mDL",
          "credentialData":null,
          "mdocData": { 
              "org.iso.18013.5.1": {
                  "family_name": "Doe",
                  "given_name": "John",
                  "birth_date": "1980-01-02"
              }
          },
          "x5Chain": ${buildJsonArray { add(LspPotentialInterop.POTENTIAL_ISSUER_CERT) }},
          "trustedRootCAs": ${buildJsonArray { add(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT) }}
       }
    """.trimIndent()

    val mDLCredentialIssuanceExample = typedValueExampleDescriptorDsl<IssuanceRequest>(mDLCredentialIssuanceData)

    // language=JSON
    val batchExampleJwt = typedValueExampleDescriptorDsl<List<IssuanceRequest>>(
        """
            [
                $openBadgeCredentialIssuance,
                $bankIdCredentialIssuance
            ]
        """.trimIndent()
    )


    // language=json
    val batchExampleSdJwt = typedValueExampleDescriptorDsl<List<IssuanceRequest>>(
        """
            [
                {
                    "issuerKey": $issuerKey,
                    "issuerDid": $issuerDid,
                    "credentialConfigurationId": "OpenBadgeCredential_sd-jwt",
                    "credentialData": $openBadgeCredentialData,
                    "mdocData": null,
                    "mapping": $mapping,
                    "selectiveDisclosure":
                    {
                        "fields":
                        {
                            "name":
                            {
                                "sd": true
                            }
                        }
                    }
                },
                {
                    "issuerKey": $issuerKey,
                    "issuerDid": $issuerDid,
                    "credentialConfigurationId": "BankId_sd-jwt",
                    "credentialData": $bankIdCredentialData,
                    "mdocData": null,
                    "mapping": $mapping,
                    "selectiveDisclosure":
                    {
                        "fields":
                        {
                            "credentialSubject":
                            {
                                "sd": true
                            }
                        }
                    }
                }
            ]
        """.trimIndent()
    )

    // language=JSON
    val sdJwtW3CExample = typedValueExampleDescriptorDsl<IssuanceRequest>(
        """
            {
                "issuerKey": $issuerKey,
                "issuerDid": $issuerDid,
                "credentialConfigurationId": "OpenBadgeCredential_sd-jwt",
                "credentialData": $openBadgeCredentialData,
                "mdocData": null,
                "mapping": $mapping,
                "selectiveDisclosure":
                {
                    "fields":
                    {
                        "name":
                        {
                            "sd": true
                        }
                    }
                }
            }
        """.trimIndent()
    )

    val issuerOnboardingRequestDefaultEd25519Example = typedValueExampleDescriptorDsl<OnboardingRequest>(
        onboardingRequestWithKey(KeyType.Ed25519)
    )

    val issuerOnboardingRequestDefaultSecp256r1Example = typedValueExampleDescriptorDsl<OnboardingRequest>(
        onboardingRequestWithKey(KeyType.secp256r1)
    )

    val issuerOnboardingRequestDefaultSecp256k1Example = typedValueExampleDescriptorDsl<OnboardingRequest>(
        onboardingRequestWithKey(KeyType.secp256k1)
    )

    val issuerOnboardingRequestDefaultRsaExample = typedValueExampleDescriptorDsl<OnboardingRequest>(
        onboardingRequestWithKey(KeyType.RSA)
    )

    // language=JSON
    val issuerOnboardingRequestTseExampleUserPass = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "tse",
                    "keyType": "Ed25519",
                    "config":
                    {
                        "server": "http://127.0.0.1:8200/v1/transit",
                        "auth":
                        {
                            "userpassPath": "userpass",
                            "username": "myuser",
                            "password": "mypassword"
                        }
                    }
                },
                "did":
                {
                    "method": "key"
                }
            }
        """.trimIndent()
    )

    // language=JSON
    val issuerOnboardingRequestTseExampleAppRole = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "tse",
                    "keyType": "Ed25519",
                    "config":
                    {
                        "server": "http://127.0.0.1:8200/v1/transit",
                        "auth":
                        {
                            "roleId": "9ec67fde-412a-d000-66fd-ba433560f092",
                            "secretId": "65015f17-1f22-39f8-9c70-85af514e98f1"
                        }
                    }
                },
                "did":
                {
                    "method": "key"
                }
            }
        """.trimIndent()
    )

    // language=JSON
    val issuerOnboardingRequestTseExampleAccessKey = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "tse",
                    "keyType": "Ed25519",
                    "config":
                    {
                        "server": "http://127.0.0.1:8200/v1/transit",
                        "auth":
                        {
                            "accessKey": "dev-only-token"
                        }
                    }
                },
                "did":
                {
                    "method": "key"
                }
            }
        """.trimIndent()
    )

    //language=JSON
    val issuerOnboardingRequestOciExample = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "oci",
                    "keyType": "secp256r1",
                    "config":
                    {
                        "vaultId": "ocid1.vault.oc1.eu-frankfurt-1.enta2fneaadmk",
                        "compartmentId": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q"
                    }
                },
                "did":
                {
                    "method": "jwk"
                }
            }
        """.trimIndent()
    )

    //language=JSON
    val issuerOnboardingResponseOciExample = typedValueExampleDescriptorDsl<IssuerOnboardingResponse>(
        """
            {
                "issuerKey":
                {
                    "type": "oci",
                    "config":
                    {
                        "vaultId": "ocid1.vault.oc1.eu-frankfurt-1.enta2fneaadmk",
                        "compartmentId": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q"
                    },
                    "id": "ocid1.key.oc1.eu-frankfurt-1.enta2fneaadmk.abtheljrlj5snthwkx7ycdmknuftght527dkyjsoz72dcogklixrsdyolo5a",
                    "_publicKey": "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"tT1DAZdtp7vUPphTxoilmr6dfZPKcPfwL8G_Ri3K0_E\",\"y\":\"JabPubkHQPK0G7O8eL3bKg75hX4Wkojb_AOepX8xdAs\"}",
                    "_keyType": "secp256r1"
                },
                "issuerDid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6InRUMURBWmR0cDd2VVBwaFR4b2lsbXI2ZGZaUEtjUGZ3TDhHX1JpM0swX0UiLCJ5IjoiSmFiUHVia0hRUEswRzdPOGVMM2JLZzc1aFg0V2tvamJfQU9lcFg4eGRBcyJ9"
            }
        """.trimIndent()
    )


    //language=JSON
    val issuerOnboardingRequestOciRestApiExample = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "oci-rest-api",
                    "keyType": "secp256r1",
                    "config":
                    {
                        "tenancyOcid": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
                        "compartmentOcid": "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q",
                        "userOcid": "ocid1.user.oc1..aaaaaaaaxjkkfjqxdqk7ldfjrxjmacmbi7sci73rbfiwpioehikavpbtqx5q",
                        "fingerprint": "bb:d4:4b:0c:c8:3a:49:15:7f:87:55:d5:2b:7e:dd:bc",
                        "managementEndpoint": "entaftlvaaemy-management.kms.eu-frankfurt-1.oraclecloud.com",
                        "cryptoEndpoint": "entaftlvaaemy-crypto.kms.eu-frankfurt-1.oraclecloud.com",
                        "signingKeyPem": "-----BEGIN PRIVATE KEY-----\n\n-----END PRIVATE KEY-----\n"
                    }
                },
                "did":
                {
                    "method": "jwk"
                }
            }
        """.trimIndent()
    )

    //language=JSON
    val issuerOnboardingResponseOciRestApiExample = typedValueExampleDescriptorDsl<IssuerOnboardingResponse>(
        """
            {
                "issuerKey":
                {
                    "type": "oci",
                    "config":
                    {
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
    )

    // language=JSON
    val issuerOnboardingRequestDidWebExample = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "jwk",
                    "keyType": "secp256k1"
                },
                "did":
                {
                    "method": "web",
                    "config":
                    {
                        "domain": "example.com",
                        "path": "optional-user-id-1234"
                    }
                }
            }
        """.trimIndent()
    )

    // language=JSON
    val issuerOnboardingResponseDidWebExample = typedValueExampleDescriptorDsl<IssuerOnboardingResponse>(
        """
            {
                "issuerKey":
                {
                    "type": "jwk",
                    "jwk":
                    {
                        "kty": "EC",
                        "d": "sMjI1SVu4vKHLr3JwgUMu10Ihn5OL0sCaqjfZP8xpUU",
                        "crv": "secp256k1",
                        "kid": "Si07jIXqLsMKHy0vgyvPbcIvIPxdqL7Qs6STqrx1UC8",
                        "x": "q-LZDK-TZQSUczy_1K6TBFeVn60rMv4KjYvTePy2TGs",
                        "y": "qTbiSREfWRZtAKZsW-k-0BHIIYpAN0fhnjaqeMIU5OY"
                    }
                },
                "issuerDid": "did:web:example.com:optional-user-id-1234"
            }
        """.trimIndent()
    )

    // language=JSON
    val issuerOnboardingResponseDefaultExample = typedValueExampleDescriptorDsl<IssuerOnboardingResponse>(
        """
            {
                "issuerKey":
                {
                    "type": "jwk",
                    "jwk":
                    {
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
    )

    // language=JSON
    val issuerOnboardingResponseTseExample = typedValueExampleDescriptorDsl<IssuerOnboardingResponse>(
        """
            {
                "issuerKey":
                {
                    "type": "tse",
                    "server": "http://127.0.0.1:8200/v1/transit",
                    "accessKey": "dev-only-token",
                    "id": "k208278175",
                    "_publicKey":
                    [
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
    )

    // language=json
    private val sdjwt_vc_identity_credential = """
    {
     "vct": "identity_credential_vc+sd-jwt",
     "given_name": "John",
     "family_name": "Doe",
     "email": "johndoe@example.com",
     "phone_number": "+1-202-555-0101",
     "address": {
       "street_address": "123 Main St",
       "locality": "Anytown",
       "region": "Anystate",
       "country": "US"
     },
     "birthdate": "1940-01-01",
     "is_over_18": true,
     "is_over_21": true,
     "is_over_65": true
    }
    """.trimIndent()

    // language=json
    val sdJwtVCData = """
            {
                "issuerKey": { 
                "type": "jwk",
                "jwk": ${Json.parseToJsonElement(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_JWK_KEY.jwk!!)}
            },
            "issuerDid": "",
            "credentialConfigurationId": "identity_credential_vc+sd-jwt",
            "credentialData": $sdjwt_vc_identity_credential,
            "mdocData": null,
            "mapping": $mapping,
            "selectiveDisclosure":
            {
                "fields":
                {
                    "birthdate":
                    {
                        "sd": true
                    },
                    "family_name":
                    {
                        "sd": false
                    }
                }
            },
            "x5Chain": ${buildJsonArray { add(LspPotentialInterop.POTENTIAL_ISSUER_CERT) }},
            "trustedRootCAs": ${buildJsonArray { add(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT) }}
        }
    """.trimIndent()

    val sdJwtVCExample = typedValueExampleDescriptorDsl<IssuanceRequest>(sdJwtVCData)
}

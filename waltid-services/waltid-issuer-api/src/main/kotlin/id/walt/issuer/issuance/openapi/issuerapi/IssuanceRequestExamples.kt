package id.walt.issuer.issuance.openapi.issuerapi

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.IssuerOnboardingResponse
import id.walt.issuer.issuance.OnboardingRequest
import id.walt.w3c.utils.VCFormat
import io.github.smiley4.ktoropenapi.config.ValueExampleDescriptorConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

object IssuanceExamples {

    private inline fun <reified T> typedValueExampleDescriptorDsl(content: String): ValueExampleDescriptorConfig<T>.() -> Unit =
        {
            value = Json.decodeFromString<T>(content)
        }

    val ISSUER_JWK_KEY = runBlocking {
        JWKKey.importJWK("""
            {
                "kty": "EC",
                "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                "crv": "P-256",
                "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
                "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
            }
        """.trimIndent()).getOrThrow()
    }

    val ISSUER_DID = "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6IkcwUklOQmlGLW9RVUQzZDVER25lZ1F1WGVuSTI5SkRhTUdvTXZpb0tSQk0iLCJ5IjoiZWQzZUZHczJwRXRycDd2QVo3QkxjYnJVdHBLa1lXQVQySlBVUUs0bE40RSJ9"

    val ISSUER_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB\n" +
            "-----END CERTIFICATE-----"

    val ROOT_CA_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIBZTCCAQugAwIBAgII2x50/ui7K2wwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMTURPQyBST09UIENBMCAXDTI1MDUxNDE0MDI1M1oYDzIwNzUwNTAyMTQwMjUzWjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARY/Swb4KSMi1n0p8zewsX6ssZvwdgJ+eWwgf81YmOJeRPHnuvIMth9NTpBdi6RUodKrowR5u9A+pMlPVuVn/F4oz8wPTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUxaGwGuK+ZbdzYNqADTyJ/gqLRwkwCgYIKoZIzj0EAwIDSAAwRQIhAOEYhbDYF/1kgDgy4anwZfoULmwt4vt08U6EU2AjXI09AiACCM7m3FnO7bc+xYQRT+WBkZXe/Om4bVmlIK+av+SkCA==\n" +
            "-----END CERTIFICATE-----\n"

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

    val pda1CredentialData = """
        {
        "@context": [
            "https://www.w3.org/2018/credentials/v1"
        ],
        "id": "https://www.w3.org/2018/credentials/v1",
        "type": [
            "VerifiableCredential",
            "VerifiableAttestation",
            "VerifiablePortableDocumentA1"
        ],
        "issuer": "did:ebsi:zf39qHTXaLrr6iy3tQhT3UZ",
        "issuanceDate": "2020-03-10T04:24:12Z",
        "credentialSubject": {
            "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz",
            "section1": {
                "personalIdentificationNumber": "1",
                "sex": "01",
                "surname": "Savvaidis",
                "forenames": "Charalampos",
                "dateBirth": "1985-08-15",
                "nationalities": [
                    "BE"
                ],
                "stateOfResidenceAddress": {
                    "streetNo": "sss, nnn ",
                    "postCode": "ppp",
                    "town": "ccc",
                    "countryCode": "BE"
                },
                "stateOfStayAddress": {
                    "streetNo": "sss, nnn ",
                    "postCode": "ppp",
                    "town": "ccc",
                    "countryCode": "BE"
                }
            },
            "section2": {
                "memberStateWhichLegislationApplies": "DE",
                "startingDate": "2022-10-09",
                "endingDate": "2022-10-29",
                "certificateForDurationActivity": true,
                "determinationProvisional": false,
                "transitionRulesApplyAsEC8832004": false
            },
            "section3": {
                "postedEmployedPerson": false,
                "employedTwoOrMoreStates": false,
                "postedSelfEmployedPerson": true,
                "selfEmployedTwoOrMoreStates": true,
                "civilServant": true,
                "contractStaff": false,
                "mariner": false,
                "employedAndSelfEmployed": false,
                "civilAndEmployedSelfEmployed": true,
                "flightCrewMember": false,
                "exception": false,
                "exceptionDescription": "",
                "workingInStateUnder21": false
            },
            "section4": {
                "employee": false,
                "selfEmployedActivity": true,
                "nameBusinessName": "1",
                "registeredAddress": {
                    "streetNo": "1, 1 1",
                    "postCode": "1",
                    "town": "1",
                    "countryCode": "DE"
                }
            },
            "section5": {
                "noFixedAddress": true
            },
            "section6": {
                "name": "National Institute for the Social Security of the Self-employed (NISSE)",
                "address": {
                    "streetNo": "Quai de Willebroeck 35",
                    "postCode": "1000",
                    "town": "Bruxelles",
                    "countryCode": "BE"
                },
                "institutionID": "NSSIE/INASTI/RSVZ",
                "officeFaxNo": "",
                "officePhoneNo": "0800 12 018",
                "email": "info@rsvz-inasti.fgov.be",
                "date": "2022-10-28",
                "signature": "Official signature"
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

    private val issuerKeyEbsi = """
    {
        "type": "jwk",
        "jwk": {
            "kty": "EC",
            "x": "SgfOvOk1TL5yiXhK5Nq7OwKfn_RUkDizlIhAf8qd2wE",
            "y": "u_y5JZOsw3SrnNPydzJkoaiqb8raSdCNE_nPovt1fNI",
            "crv": "P-256",
            "d": "UqSi2MbJmPczfRmwRDeOJrdivoEy-qk4OEDjFwJYlUI"
        }
    }
    """.trimIndent()
    private const val issuerDidEbsi = "\"did:ebsi:zf39qHTXaLrr6iy3tQhT3UZ\""


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

    private val pda1Mapping = """
    {
            "id": "<uuid>",
            "issuer": "<issuerDid>",
            "credentialSubject": {
                "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp-ebsi>",
            "issued": "<timestamp-ebsi>",
            "validFrom": "<timestamp-ebsi>",
            "expirationDate": "<timestamp-ebsi-in:365d>",
            "credentialSchema": {
                "id": "https://api-conformance.ebsi.eu/trusted-schemas-registry/v3/schemas/z5qB8tydkn3Xk3VXb15SJ9dAWW6wky1YEoVdGzudWzhcW",
                "type": "FullJsonSchemaValidator2021"
            }
    }
    """.trimIndent()


    private val ietfSdJwtmapping = """
        {
            "id":"<uuid>",
            "iat": "<timestamp-seconds>",
            "nbf": "<timestamp-seconds>",
            "exp": "<timestamp-in-seconds:365d>"
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
                    "credentialConfigurationId": "OpenBadgeCredential_${VCFormat.jwt_vc.value}",
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
                    "credentialConfigurationId": "BankId_${VCFormat.jwt_vc.value}",
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
                "credentialConfigurationId": "OpenBadgeCredential_${VCFormat.jwt_vc_json.value}",
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

    val sdJwtW3CPDA1Example = typedValueExampleDescriptorDsl<IssuanceRequest>(
        """
            {
                "issuerKey": $issuerKeyEbsi,
                "issuerDid": $issuerDidEbsi,
                "credentialConfigurationId": "VerifiablePortableDocumentA1_${VCFormat.jwt_vc.value}",
                "credentialData": $pda1CredentialData,
                "mdocData": null,
                "mapping": $pda1Mapping,
                 "selectiveDisclosure": {
                        "fields": {
                            "credentialSubject": {
                                "sd": false,
                                "children": {
                                    "fields": {
                                        "section1": {
                                            "sd": false,
                                            "children": {
                                                "fields": {
                                                    "personalIdentificationNumber": {
                                                        "sd": true
                                                    },
                                                    "sex": {
                                                        "sd": true
                                                    },
                                                    "surname": {
                                                        "sd": true
                                                    },
                                                    "forenames": {
                                                        "sd": true
                                                    },
                                                    "dateBirth": {
                                                        "sd": true
                                                    },
                                                    "nationalities": {
                                                        "sd": true
                                                    },
                                                    "stateOfResidenceAddress": {
                                                        "sd": true
                                                    },
                                                    "stateOfStayAddress": {
                                                        "sd": true
                                                    }
                                                }
                                            }
                                        },
                                        "section3": {
                                            "sd": false,
                                            "children": {
                                                "fields": {
                                                    "postedEmployedPerson": {
                                                        "sd": true
                                                    },
                                                    "employedTwoOrMoreStates": {
                                                        "sd": true
                                                    },
                                                    "postedSelfEmployedPerson": {
                                                        "sd": true
                                                    },
                                                    "selfEmployedTwoOrMoreStates": {
                                                        "sd": true
                                                    },
                                                    "civilServant": {
                                                        "sd": true
                                                    },
                                                    "contractStaff": {
                                                        "sd": true
                                                    },
                                                    "mariner": {
                                                        "sd": true
                                                    },
                                                    "employedAndSelfEmployed": {
                                                        "sd": true
                                                    },
                                                    "civilAndEmployedSelfEmployed": {
                                                        "sd": true
                                                    },
                                                    "flightCrewMember": {
                                                        "sd": true
                                                    },
                                                    "exception": {
                                                        "sd": true
                                                    },
                                                    "exceptionDescription": {
                                                        "sd": true
                                                    },
                                                    "workingInStateUnder21": {
                                                        "sd": true
                                                    }
                                                }
                                            }
                                        },
                                        "section4": {
                                            "sd": false,
                                            "children": {
                                                "fields": {
                                                    "employee": {
                                                        "sd": true
                                                    },
                                                    "selfEmployedActivity": {
                                                        "sd": true
                                                    },
                                                    "nameBusinessName": {
                                                        "sd": true
                                                    },
                                                    "registeredAddress": {
                                                        "sd": true
                                                    }
                                                }
                                            }
                                        },
                                        "section5": {
                                            "sd": false,
                                            "children": {
                                                "fields": {
                                                    "noFixedAddress": {
                                                        "sd": true
                                                    }
                                                }
                                            }
                                        },
                                        "section6": {
                                            "sd": false,
                                            "children": {
                                                "fields": {
                                                    "name": {
                                                        "sd": true
                                                    },
                                                    "address": {
                                                        "sd": true
                                                    },
                                                    "institutionID": {
                                                        "sd": true
                                                    },
                                                    "officeFaxNo": {
                                                        "sd": true
                                                    },
                                                    "officePhoneNo": {
                                                        "sd": true
                                                    },
                                                    "email": {
                                                        "sd": true
                                                    },
                                                    "date": {
                                                        "sd": true
                                                    },
                                                    "signature": {
                                                        "sd": true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
    val issuerOnboardingRequestAwsRestApiExampleWithDirectAccess = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "aws-rest-api",
                    "keyType": "secp256r1",
                    "config":
                    {
                       "auth": {
                            "accessKeyId": "AKIA........QU5F",
                            "secretAccessKey": "6YDr..................7Sr",
                            "region": "eu-central-1"
                       }
                      
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
    val issuerOnboardingRequestAwsSdkExample = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "aws",
                    "keyType": "secp256r1",
                    "config":
                    {
                        "auth": {
                            "region": "eu-central-1"
                       },
                       "keyName" : "waltid-key",
                        "tags": {
                         
                            "project": "waltid",
                            "owner": "identity-team"
                       
                            }
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
    val issuerOnboardingRequestAwsRestApiExampleWithRole = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "aws-rest-api",
                    "keyType": "secp256r1",
                    "config":
                    {
                       "auth": {
                            "roleName": "access",
                            "region": "eu-central-1"
                       }
                      
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
    val issuerOnboardingRequestAzureRestApiExample = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
            {
                "key":
                {
                    "backend": "azure-rest-api",
                    "keyType": "secp256r1",
                    "config":
                    {
                       "auth": {
                            "clientId": "client id",
                            "clientSecret": "client secret",
                            "tenantId": "tenant id",
                            "keyVaultUrl": "url to the vault"
                       }
                      
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
    val issuerOnboardingRequestAzureSdkExample = typedValueExampleDescriptorDsl<OnboardingRequest>(
        """
              {
                "key":
                {
                    "backend": "azure",
                    "keyType": "secp256r1",
                    "config":
                    {
                      "auth": {
                         "keyVaultUrl" : "url to the vault"
                      },
                      "tags": {
                         
                            "project": "waltid",
                            "owner": "identity-team"
                       
                            }
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
                "jwk": ${Json.parseToJsonElement(ISSUER_JWK_KEY.jwk!!)}
            },
            "credentialConfigurationId": "identity_credential_vc+sd-jwt",
            "credentialData": $sdjwt_vc_identity_credential,
            "mdocData": null,
            "mapping": $ietfSdJwtmapping,
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
            "x5Chain": ${buildJsonArray { add(ISSUER_CERT) }},
            "trustedRootCAs": ${buildJsonArray { add(ROOT_CA_CERT) }}
        }
    """.trimIndent()

    val sdJwtVCExample = typedValueExampleDescriptorDsl<IssuanceRequest>(sdJwtVCData)

    val sdJwtVCDataWithIssuerDid = """
        {
            "issuerKey": { 
                "type": "jwk",
                "jwk": ${Json.parseToJsonElement(ISSUER_JWK_KEY.jwk!!)}
            },
            "credentialConfigurationId": "identity_credential_vc+sd-jwt",
            "credentialData": $sdjwt_vc_identity_credential,
            "mdocData": null,
            "mapping": $ietfSdJwtmapping,
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
            "issuerDid": "$ISSUER_DID"
        }
    """.trimIndent()

    val sdJwtVCWithIssuerDidExample = typedValueExampleDescriptorDsl<IssuanceRequest>(sdJwtVCDataWithIssuerDid)

    val sdJwtVCDataWithSDSub = """
        {
            "issuerKey": { 
                "type": "jwk",
                "jwk": ${Json.parseToJsonElement(ISSUER_JWK_KEY.jwk!!)}
            },
            "credentialConfigurationId": "identity_credential_vc+sd-jwt",
            "credentialData": $sdjwt_vc_identity_credential,
            "mdocData": null,
            "mapping": $ietfSdJwtmapping,
            "selectiveDisclosure":
            {
                "fields":
                {
                    "birthdate":
                    {
                        "sd": true
                    },
                    "sub":
                    {
                        "sd": true
                    },
                    "iat":
                    {
                        "sd": true
                    }
                }
            },
            "x5Chain": ${buildJsonArray { add(ISSUER_CERT) }},
            "trustedRootCAs": ${buildJsonArray { add(ROOT_CA_CERT) }}
        }
    """.trimIndent()

    val sdJwtVCExampleWithSDSub = typedValueExampleDescriptorDsl<IssuanceRequest>(sdJwtVCDataWithSDSub)

    val ebsiCTExampleAuthInTimeDraft11 =  typedValueExampleDescriptorDsl<IssuanceRequest>("""
        {
          "credentialConfigurationId": "InTimeIssuance_jwt_vc",
          "standardVersion": "DRAFT11",
          "issuerKey": {
                  "type": "jwk",
                  "jwk": {
                    "kty": "EC",
                    "x": "zK8OWXyBYBH0PJxMf5CsbVeGBDoNNHgcUfXN2fjUazs",
                    "y": "FcMlAJxSKsvmN9RQPkPZYvJnju7xZLuVEGHi7zatwX0",
                    "crv": "P-256",
                    "d": "JhoVo4fRJibCSlREtXJdgKKCHShMgarjWRL5MzCd9Qw"
                }
          },
          "issuerDid": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbtANUSeJyVFB45Gh1at2EMcHbEoMmJVSpaGEu4xGk8b8susD83jxL3jZJ4VbNcq3diik4RVCi3ea6VPfjNNCEyESEWK4w5z89uezUUUc13ssTPkncXEUeoKayqCbX4aJLfW",
          "authenticationMethod": "ID_TOKEN"
          "credentialData": {
            "@context": [
                "https://www.w3.org/2018/credentials/v1"
            ],
            "id": "https://www.w3.org/2018/credentials/123",
            "type": [
                "VerifiableCredential",
                "VerifiableAttestation",
                "InTimeIssuance"
            ],
            "issuanceDate": "2020-03-10T04:24:12Z",
            "credentialSubject": {
                "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz"
            }
          },
          "mapping": {
            "id": "<uuid>",
            "issuer": "<issuerDid>",
            "credentialSubject": {
                "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp-ebsi>",
            "issued": "<timestamp-ebsi>",
            "validFrom": "<timestamp-ebsi>",
            "expirationDate": "<timestamp-ebsi-in:365d>",
            "credentialSchema": {
              "id": "https://api-pilot.ebsi.eu/trusted-schemas-registry/v3/schemas/zDpWGUBenmqXzurskry9Nsk6vq2R8thh9VSeoRqguoyMD",
              "type": "FullJsonSchemaValidator2021"
            }
          },
          "useJar": true,
          "draft11EncodeOfferedCredentialsByReference": false
        }
    """.trimIndent()
    )

    val ebsiCTExampleAuthDeferredDraft11 =  typedValueExampleDescriptorDsl<IssuanceRequest>("""
        {
          "credentialConfigurationId": "DeferredIssuance_jwt_vc",
          "standardVersion": "DRAFT11",
          "issuerKey": {
                  "type": "jwk",
                  "jwk": {
                    "kty": "EC",
                    "x": "zK8OWXyBYBH0PJxMf5CsbVeGBDoNNHgcUfXN2fjUazs",
                    "y": "FcMlAJxSKsvmN9RQPkPZYvJnju7xZLuVEGHi7zatwX0",
                    "crv": "P-256",
                    "d": "JhoVo4fRJibCSlREtXJdgKKCHShMgarjWRL5MzCd9Qw"
                }
          },
          "issuerDid": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbtANUSeJyVFB45Gh1at2EMcHbEoMmJVSpaGEu4xGk8b8susD83jxL3jZJ4VbNcq3diik4RVCi3ea6VPfjNNCEyESEWK4w5z89uezUUUc13ssTPkncXEUeoKayqCbX4aJLfW",
          "authenticationMethod": "ID_TOKEN"
          "credentialData": {
            "@context": [
                "https://www.w3.org/2018/credentials/v1"
            ],
            "id": "https://www.w3.org/2018/credentials/123",
            "type": [
                "VerifiableCredential",
                "VerifiableAttestation",
                "DeferredIssuance"
            ],
            "issuanceDate": "2020-03-10T04:24:12Z",
            "credentialSubject": {
                "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz"
            }
          },
          "mapping": {
            "id": "<uuid>",
            "issuer": "<issuerDid>",
            "credentialSubject": {
                "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp-ebsi>",
            "issued": "<timestamp-ebsi>",
            "validFrom": "<timestamp-ebsi>",
            "expirationDate": "<timestamp-ebsi-in:365d>",
            "credentialSchema": {
              "id": "https://api-pilot.ebsi.eu/trusted-schemas-registry/v3/schemas/zDpWGUBenmqXzurskry9Nsk6vq2R8thh9VSeoRqguoyMD",
              "type": "FullJsonSchemaValidator2021"
            }
          },
          "useJar": true,
          "draft11EncodeOfferedCredentialsByReference": false,
          "issuanceType": "DEFERRED"
        }
    """.trimIndent()
    )

    val ebsiCTExamplePreAuthDraft11 =  typedValueExampleDescriptorDsl<IssuanceRequest>("""
        {
          "credentialConfigurationId": "PreAuthIssuance_jwt_vc",
          "standardVersion": "DRAFT11",
          "issuerKey": {
                  "type": "jwk",
                  "jwk": {
                    "kty": "EC",
                    "x": "zK8OWXyBYBH0PJxMf5CsbVeGBDoNNHgcUfXN2fjUazs",
                    "y": "FcMlAJxSKsvmN9RQPkPZYvJnju7xZLuVEGHi7zatwX0",
                    "crv": "P-256",
                    "d": "JhoVo4fRJibCSlREtXJdgKKCHShMgarjWRL5MzCd9Qw"
                }
          },
          "issuerDid": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbtANUSeJyVFB45Gh1at2EMcHbEoMmJVSpaGEu4xGk8b8susD83jxL3jZJ4VbNcq3diik4RVCi3ea6VPfjNNCEyESEWK4w5z89uezUUUc13ssTPkncXEUeoKayqCbX4aJLfW",
          "authenticationMethod": "PRE_AUTHORIZED"
          "credentialData": {
            "@context": [
                "https://www.w3.org/2018/credentials/v1"
            ],
            "id": "https://www.w3.org/2018/credentials/123",
            "type": [
                "VerifiableCredential",
                "VerifiableAttestation",
                "PreAuthIssuance"
            ],
            "issuanceDate": "2020-03-10T04:24:12Z",
            "credentialSubject": {
                "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz"
            }
          },
          "mapping": {
            "id": "<uuid>",
            "issuer": "<issuerDid>",
            "credentialSubject": {
                "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp-ebsi>",
            "issued": "<timestamp-ebsi>",
            "validFrom": "<timestamp-ebsi>",
            "expirationDate": "<timestamp-ebsi-in:365d>",
            "credentialSchema": {
              "id": "https://api-pilot.ebsi.eu/trusted-schemas-registry/v3/schemas/zDpWGUBenmqXzurskry9Nsk6vq2R8thh9VSeoRqguoyMD",
              "type": "FullJsonSchemaValidator2021"
            }
          },
          "draft11EncodeOfferedCredentialsByReference": false
        }
    """.trimIndent()
    )

    val ebsiCTExampleAuthInTimeDraft13 =  typedValueExampleDescriptorDsl<IssuanceRequest>("""
        {
          "credentialConfigurationId": "InTimeIssuance_jwt_vc",
          "standardVersion": "DRAFT13",
          "issuerKey": {
                  "type": "jwk",
                  "jwk": {
                    "kty": "EC",
                    "x": "zK8OWXyBYBH0PJxMf5CsbVeGBDoNNHgcUfXN2fjUazs",
                    "y": "FcMlAJxSKsvmN9RQPkPZYvJnju7xZLuVEGHi7zatwX0",
                    "crv": "P-256",
                    "d": "JhoVo4fRJibCSlREtXJdgKKCHShMgarjWRL5MzCd9Qw"
                }
          },
          "issuerDid": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbtANUSeJyVFB45Gh1at2EMcHbEoMmJVSpaGEu4xGk8b8susD83jxL3jZJ4VbNcq3diik4RVCi3ea6VPfjNNCEyESEWK4w5z89uezUUUc13ssTPkncXEUeoKayqCbX4aJLfW",
          "authenticationMethod": "ID_TOKEN"
          "credentialData": {
            "@context": [
                "https://www.w3.org/2018/credentials/v1"
            ],
            "id": "https://www.w3.org/2018/credentials/123",
            "type": [
                "VerifiableCredential",
                "VerifiableAttestation",
                "InTimeIssuance"
            ],
            "issuanceDate": "2020-03-10T04:24:12Z",
            "credentialSubject": {
                "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz"
            }
          },
          "mapping": {
            "id": "<uuid>",
            "issuer": "<issuerDid>",
            "credentialSubject": {
                "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp-ebsi>",
            "issued": "<timestamp-ebsi>",
            "validFrom": "<timestamp-ebsi>",
            "expirationDate": "<timestamp-ebsi-in:365d>",
            "credentialSchema": {
              "id": "https://api-pilot.ebsi.eu/trusted-schemas-registry/v3/schemas/zDpWGUBenmqXzurskry9Nsk6vq2R8thh9VSeoRqguoyMD",
              "type": "FullJsonSchemaValidator2021"
            }
          },
          "useJar": true
        }
    """.trimIndent()
    )

    val ebsiCTExampleAuthDeferredDraft13 =  typedValueExampleDescriptorDsl<IssuanceRequest>("""
        {
          "credentialConfigurationId": "DeferredIssuance_jwt_vc",
          "standardVersion": "DRAFT13",
          "issuerKey": {
                  "type": "jwk",
                  "jwk": {
                    "kty": "EC",
                    "x": "zK8OWXyBYBH0PJxMf5CsbVeGBDoNNHgcUfXN2fjUazs",
                    "y": "FcMlAJxSKsvmN9RQPkPZYvJnju7xZLuVEGHi7zatwX0",
                    "crv": "P-256",
                    "d": "JhoVo4fRJibCSlREtXJdgKKCHShMgarjWRL5MzCd9Qw"
                }
          },
          "issuerDid": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbtANUSeJyVFB45Gh1at2EMcHbEoMmJVSpaGEu4xGk8b8susD83jxL3jZJ4VbNcq3diik4RVCi3ea6VPfjNNCEyESEWK4w5z89uezUUUc13ssTPkncXEUeoKayqCbX4aJLfW",
          "authenticationMethod": "ID_TOKEN"
          "credentialData": {
            "@context": [
                "https://www.w3.org/2018/credentials/v1"
            ],
            "id": "https://www.w3.org/2018/credentials/123",
            "type": [
                "VerifiableCredential",
                "VerifiableAttestation",
                "DeferredIssuance"
            ],
            "issuanceDate": "2020-03-10T04:24:12Z",
            "credentialSubject": {
                "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz"
            }
          },
          "mapping": {
            "id": "<uuid>",
            "issuer": "<issuerDid>",
            "credentialSubject": {
                "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp-ebsi>",
            "issued": "<timestamp-ebsi>",
            "validFrom": "<timestamp-ebsi>",
            "expirationDate": "<timestamp-ebsi-in:365d>",
            "credentialSchema": {
              "id": "https://api-pilot.ebsi.eu/trusted-schemas-registry/v3/schemas/zDpWGUBenmqXzurskry9Nsk6vq2R8thh9VSeoRqguoyMD",
              "type": "FullJsonSchemaValidator2021"
            }
          },
          "useJar": true,
          "issuanceType": "DEFERRED"
        }
    """.trimIndent()
    )

    val ebsiCTExamplePreAuthDraft13 =  typedValueExampleDescriptorDsl<IssuanceRequest>("""
        {
          "credentialConfigurationId": "PreAuthIssuance_jwt_vc",
          "standardVersion": "DRAFT13",
          "issuerKey": {
                  "type": "jwk",
                  "jwk": {
                    "kty": "EC",
                    "x": "zK8OWXyBYBH0PJxMf5CsbVeGBDoNNHgcUfXN2fjUazs",
                    "y": "FcMlAJxSKsvmN9RQPkPZYvJnju7xZLuVEGHi7zatwX0",
                    "crv": "P-256",
                    "d": "JhoVo4fRJibCSlREtXJdgKKCHShMgarjWRL5MzCd9Qw"
                }
          },
          "issuerDid": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbtANUSeJyVFB45Gh1at2EMcHbEoMmJVSpaGEu4xGk8b8susD83jxL3jZJ4VbNcq3diik4RVCi3ea6VPfjNNCEyESEWK4w5z89uezUUUc13ssTPkncXEUeoKayqCbX4aJLfW",
          "authenticationMethod": "PRE_AUTHORIZED"
          "credentialData": {
            "@context": [
                "https://www.w3.org/2018/credentials/v1"
            ],
            "id": "https://www.w3.org/2018/credentials/123",
            "type": [
                "VerifiableCredential",
                "VerifiableAttestation",
                "PreAuthIssuance"
            ],
            "issuanceDate": "2020-03-10T04:24:12Z",
            "credentialSubject": {
                "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz"
            }
          },
          "mapping": {
            "id": "<uuid>",
            "issuer": "<issuerDid>",
            "credentialSubject": {
                "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp-ebsi>",
            "issued": "<timestamp-ebsi>",
            "validFrom": "<timestamp-ebsi>",
            "expirationDate": "<timestamp-ebsi-in:365d>",
            "credentialSchema": {
              "id": "https://api-pilot.ebsi.eu/trusted-schemas-registry/v3/schemas/zDpWGUBenmqXzurskry9Nsk6vq2R8thh9VSeoRqguoyMD",
              "type": "FullJsonSchemaValidator2021"
            }
          }
        }
    """.trimIndent()
    )

}

import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.issuer.issuance.openapi.issuerapi.IssuanceExamples
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.util.JwtUtils
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.assertContains
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class InputDescriptorMatchingTest(
    private val issuerApi: IssuerApi,
    private val exchangeApi: ExchangeApi,
    private val sessionApi: Verifier.SessionApi,
    private val verificationApi: Verifier.VerificationApi
) {
    @OptIn(ExperimentalUuidApi::class)
    fun e2e(wallet: Uuid, did: String) = runTest {
        /*
        Issue credential:
        {
            type: [ "VerifiableCredential", "UniversityDegree" ]
            ...
            credentialSubject: {
                degree: {
                    type: "BachelorDegree"
                }
            }
        }
         */
        val newCredential1 = issueCredential(universityDegreeIssuanceRequest, wallet, false)
        // issue second credential to test input descriptor matching on wallet (credential should not be found for the presentation definition below)
        issueCredential(Json.decodeFromString(IssuanceExamples.sdJwtVCData), wallet, true)

        // Presentation/Verification
        // Request: $.type: "UniversityDegree",
        // --> match should return 1, presentation should be accepted
        verifyCredential(getPresentationRequestByType("UniversityDegree"), wallet, did, newCredential1, true)
        // Request: $.type: "XYZ",
        // --> match should return 0, presentation should be rejected
        verifyCredential(getPresentationRequestByType("XYZ"), wallet, did, newCredential1, false)
        // Request: $.type: "BachelorDegree",
        // --> match should return 0, presentation should be rejected
        verifyCredential(getPresentationRequestByType("BachelorDegree"), wallet, did, newCredential1, false)
        // Request: $.type: "UniversityDegree" and $.credentialSubject.degree.type: "BachelorDegree",
        // --> match should return 1, presentation should be accepted
        verifyCredential(
            getPresentationRequestByType("UniversityDegree", "BachelorDegree"),
            wallet,
            did,
            newCredential1,
            true
        )
        // Request: $.type: "BachelorDegree" and $.credentialSubject.degree.type: "UniversityDegree",
        // --> match should return 0, presentation should be rejected
        verifyCredential(
            getPresentationRequestByType("BachelorDegree", "UniversityDegree"),
            wallet,
            did,
            newCredential1,
            false
        )
        // Request: $.credentialSubject.degree.type: "BachelorDegree",
        // --> match should return 1, presentation should be accepted
        verifyCredential(getPresentationRequestByDegreeType("BachelorDegree"), wallet, did, newCredential1, true)
        // Request: $.credentialSubject.degree.type: "UniversityDegree",
        // --> match should return 0, presentation should be rejected
        verifyCredential(getPresentationRequestByDegreeType("UniversityDegree"), wallet, did, newCredential1, false)
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun issueCredential(
        issuanceRequest: IssuanceRequest,
        wallet: Uuid,
        sdJwt: Boolean
    ): WalletCredential {
        lateinit var offerUrl: String
        if (sdJwt) {
            issuerApi.sdjwt(issuanceRequest) {
                offerUrl = it
                println("offer: $offerUrl")
            }
        } else {
            issuerApi.jwt(issuanceRequest) {
                offerUrl = it
                println("offer: $offerUrl")
            }
        }
        //region -Exchange / claim-
        lateinit var newCredential: WalletCredential
        exchangeApi.resolveCredentialOffer(wallet, offerUrl)
        exchangeApi.useOfferRequest(wallet, offerUrl, 1) {
            newCredential = it.first()
        }
        if (sdJwt)
            assertContains(JwtUtils.parseJWTPayload(newCredential.document).keys, "vct")
        else
            assertContains(JwtUtils.parseJWTPayload(newCredential.document).keys, JwsSignatureScheme.JwsOption.VC)

        return newCredential
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun verifyCredential(
        presentationRequest: String,
        wallet: Uuid,
        did: String,
        credentialToPresent: WalletCredential,
        shouldBeSuccess: Boolean
    ) {
        //region -Verifier / request url-
        lateinit var verificationUrl: String
        lateinit var verificationId: String
        verificationApi.verify(presentationRequest) {
            verificationUrl = it
            verificationId = Url(verificationUrl).parameters.getOrFail("state")
        }
        //endregion -Verifier / request url-

        //region -Exchange / match presentation 1-
        lateinit var resolvedPresentationOfferString: String
        lateinit var presentationDefinition: String
        exchangeApi.resolvePresentationRequest(wallet, verificationUrl) {
            resolvedPresentationOfferString = it
            presentationDefinition = Url(it).parameters.getOrFail("presentation_definition")
        }
        exchangeApi.matchCredentialsForPresentationDefinition(
            wallet, presentationDefinition,
            if (shouldBeSuccess)
                listOf(credentialToPresent.id)
            else
                listOf()
        )
        // end region

        // region -Present credential-
        val usePresentationReq = UsePresentationRequest(
            did, resolvedPresentationOfferString, listOf(credentialToPresent.id),
            credentialToPresent.disclosures?.let { mapOf(credentialToPresent.id to listOf(it)) })
        if (shouldBeSuccess)
            exchangeApi.usePresentationRequest(wallet, usePresentationReq, expectSuccess)
        else
            exchangeApi.usePresentationRequest(wallet, usePresentationReq, expectFailure)
        // end region
    }

    val universityDegreeIssuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(buildJsonObject {
        put("issuerKey", Json.decodeFromString<JsonElement>(WaltidServicesE2ETests.issuerKey))
        put("issuerDid", WaltidServicesE2ETests.issuerDid)
        put("credentialConfigurationId", "UniversityDegree_jwt_vc_json")
        put(
            "credentialData", Json.decodeFromString<JsonElement>(
                """
      {
        "@context": [
          "https://www.w3.org/2018/credentials/v1",
          "https://www.w3.org/2018/credentials/examples/v1"
        ],
        "id": "http://example.gov/credentials/3732",
        "type": [
          "VerifiableCredential",
          "UniversityDegree"
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
            )
        )
        put(
            "mapping", Json.decodeFromString<JsonElement>(
                """
      {
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
    """.trimIndent()
            )
        )
        put(
            "selectiveDisclosure", Json.decodeFromString(
                """
      {
        "fields": {
          "credentialSubject": {
            "sd": false,
             "children": {
               "fields": {
                "degree": {
                  "sd": false,
                  "children": {
                    "fields": {
                      "type": {
                        "sd": true
                      }
                    }
                  }
                }
              }
            }
          }
        },
        "decoyMode": "NONE",
        "decoys": 0
      }
    """.trimIndent()
            )
        )
    })

    fun getPresentationRequestByType(type: String, degreeType: String = ".*") = """
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
        {
          "format": "jwt_vc_json",
          "input_descriptor": {
            "id": "IdIsRequired",
            "constraints": {
              "fields": [
                {
                  "path": [
                    "${'$'}.vc.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": "$type"
                  }
                },
                {
                  "path": [
                    "${'$'}.vc.credentialSubject.degree.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": "$degreeType"
                  }
                }
              ],
              "limit_disclosure": "required"
            }
          }
        }
      ]
    }
  """.trimIndent()

    fun getPresentationRequestByDegreeType(degreeType: String) = """
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
        {
          "format": "jwt_vc_json",
          "input_descriptor": {
            "id": "IdIsRequired",
            "constraints": {
              "fields": [
                {
                  "path": [
                    "${'$'}.vc.credentialSubject.degree.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": "$degreeType"
                  }
                }
              ],
              "limit_disclosure": "required"
            }
          }
        }
      ]
    }
  """.trimIndent()
}

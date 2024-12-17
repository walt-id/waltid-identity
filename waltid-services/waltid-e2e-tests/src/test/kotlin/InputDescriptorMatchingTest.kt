import id.walt.credentials.schemes.JwsSignatureScheme
import id.walt.issuer.issuance.IssuanceExamples
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.util.JwtUtils
import id.walt.webwallet.db.models.WalletCredential
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
    lateinit var offerUrl: String
    issuerApi.jwt(issuanceRequest1) {
      offerUrl = it
      println("offer: $offerUrl")
    }
    //region -Exchange / claim-
    lateinit var newCredential1: WalletCredential
    exchangeApi.resolveCredentialOffer(wallet, offerUrl)
    exchangeApi.useOfferRequest(wallet, offerUrl, 1) {
      newCredential1 = it.first()
    }
    assertContains(JwtUtils.parseJWTPayload(newCredential1.document).keys, JwsSignatureScheme.JwsOption.VC)

    lateinit var newCredential2: WalletCredential
    issuerApi.sdjwt(Json.decodeFromString(IssuanceExamples.sdJwtVCData)) {
      offerUrl = it
    }
    exchangeApi.resolveCredentialOffer(wallet, offerUrl)
    exchangeApi.useOfferRequest(wallet, offerUrl, 1) {
      newCredential2 = it.first()
    }
    assertContains(JwtUtils.parseJWTPayload(newCredential2.document).keys, "vct")
    //endregion -Exchange / claim-

    //region -Verifier / request url-
    lateinit var verificationUrl: String
    lateinit var verificationId: String
    verificationApi.verify(presentationRequestUniversityDegree) {
      verificationUrl = it
      verificationId = Url(verificationUrl).parameters.getOrFail("state")
    }
    //endregion -Verifier / request url-

    //region -Exchange / match presentation 1-
    lateinit var presentationDefinition1: String
    exchangeApi.resolvePresentationRequest(wallet, verificationUrl) {
      presentationDefinition1 = Url(it).parameters.getOrFail("presentation_definition")
    }
    exchangeApi.matchCredentialsForPresentationDefinition(
      wallet, presentationDefinition1, listOf(newCredential1.id)
    )

    //region -Exchange / match presentation 2-
    lateinit var verificationUrl2: String
    lateinit var verificationId2: String
    verificationApi.verify(presentationRequestXYZ) {
      verificationUrl2 = it
      verificationId2 = Url(verificationUrl).parameters.getOrFail("state")
    }
    lateinit var presentationDefinition2: String
    exchangeApi.resolvePresentationRequest(wallet, verificationUrl2) {
      presentationDefinition2 = Url(it).parameters.getOrFail("presentation_definition")
    }
    exchangeApi.matchCredentialsForPresentationDefinition(
      wallet, presentationDefinition2, listOf()
    )

  }

  val issuanceRequest1 = Json.decodeFromJsonElement<IssuanceRequest>(buildJsonObject {
    put("issuerKey", Json.decodeFromString<JsonElement>(WaltidServicesE2ETests.issuerKey))
    put("issuerDid", WaltidServicesE2ETests.issuerDid)
    put("credentialConfigurationId", "UniversityDegree_jwt_vc_json")
    put("credentialData", Json.decodeFromString<JsonElement>("""
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
    """.trimIndent()))
    put("mapping", Json.decodeFromString<JsonElement>("""
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
    """.trimIndent()))
    put("selectiveDisclosure", Json.decodeFromString("""
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
    """.trimIndent()))
  })

  val presentationRequestUniversityDegree = """
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
          "format": "vc+sd-jwt",
          "input_descriptor": {
            "id": "IdIsRequired",
            "constraints": {
              "fields": [
                {
                  "path": [
                    "${'$'}.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": "UniversityDegree"
                  }
                },
                {
                  "path": [
                    "${'$'}.credentialSubject.degree.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": ".*"
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

  val presentationRequestXYZ = """
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
          "format": "vc+sd-jwt",
          "input_descriptor": {
            "id": "IdIsRequired",
            "constraints": {
              "fields": [
                {
                  "path": [
                    "${'$'}.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": "XYZ"
                  }
                },
                {
                  "path": [
                    "${'$'}.credentialSubject.degree.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": ".*"
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

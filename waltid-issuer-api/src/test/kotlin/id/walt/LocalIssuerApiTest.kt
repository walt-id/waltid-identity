package id.walt

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.issuer.IssuanceRequest
import id.walt.issuer.base.config.ConfigManager
import id.walt.issuer.createCredentialOfferUri
import id.walt.sdjwt.SDMapBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class IssuerApiTest {

    val TEST_KEY = """
    {
       "type": "jwk",
        "jwk": {
              "kty": "OKP",
              "d": "mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI",
              "crv": "Ed25519",
              "kid": "Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8",
              "x": "T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM"
            }
     }
  """

    val TEST_ISSUER_DID = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"

    val TEST_W3VC = """
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
    """

    val TEST_MAPPING = """
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
    """

    @Test
    fun testJwt() = runTest {
        val jsonKeyObj = Json.decodeFromString<JsonObject>(TEST_KEY)
        val jsonVCObj = Json.decodeFromString<JsonObject>(TEST_W3VC)
        val w3cVc = W3CVC(jsonVCObj.toMap())
        val jsonMappingObj = Json.decodeFromString<JsonObject>(TEST_MAPPING)

        val issueRequest =
            IssuanceRequest(jsonKeyObj, TEST_ISSUER_DID, "OpenBadgeCredential_jwt_vc_json", w3cVc, jsonMappingObj)

        ConfigManager.loadConfigs(emptyArray())
        val offerUri = createCredentialOfferUri(listOf(issueRequest))

        assertEquals(true, offerUri.contains("//localhost:7002/?credential_offer"))
    }


    @Test
    fun testSdJwt() = runTest {
        val jsonKeyObj = Json.decodeFromString<JsonObject>(TEST_KEY)
        val jsonVCObj = Json.decodeFromString<JsonObject>(TEST_W3VC)
        val w3cVc = W3CVC(jsonVCObj.toMap())
        val jsonMappingObj = Json.decodeFromString<JsonObject>(TEST_MAPPING)

        val selectiveDisclosureMap = SDMapBuilder().addField("sd", true).build()
        val issueRequest = IssuanceRequest(jsonKeyObj, TEST_ISSUER_DID, "OpenBadgeCredential", w3cVc, jsonMappingObj, selectiveDisclosureMap)

        ConfigManager.loadConfigs(emptyArray())
        val offerUri = createCredentialOfferUri(listOf(issueRequest))

        assertEquals(true, offerUri.contains("//localhost:7002/?credential_offer"))
    }
}

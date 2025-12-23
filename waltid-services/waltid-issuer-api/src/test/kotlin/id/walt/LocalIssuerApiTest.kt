package id.walt

import id.walt.commons.config.ConfigManager
import id.walt.commons.events.Action
import id.walt.commons.events.IssuanceEvent
import id.walt.commons.events.Status
import id.walt.crypto.keys.KeyManager
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.createCredentialOfferUri
import id.walt.oid4vc.data.CredentialFormat
import id.walt.sdjwt.SDMapBuilder
import id.walt.w3c.vc.vcs.W3CVC
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssuerApiTest {
companion object {
    @Language("JSON")
    val TEST_KEY = """{
  "type": "jwk",
  "jwk": {
    "kty": "OKP",
    "d": "mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI",
    "crv": "Ed25519",
    "kid": "Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8",
    "x": "T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM"
  }
}"""

    val TEST_ISSUER_DID = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"

    @Language("JSON")
    val TEST_W3VC = """{
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
}"""

    @Language("JSON")
    val TEST_W3VC2 = """{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "type": [
    "VerifiableCredential",
    "BankId"
  ],
  "credentialSubject": {
    "accountId": "1234567890",
    "IBAN": "DE99123456789012345678",
    "BIC": "DEUTDEDBBER",
    "birthDate": "1958-08-17",
    "familyName": "DOE",
    "givenName": "JOHN",
    "id": "identity#bankId"
  },
  "id": "identity#BankId#3add94f4-28ec-42a1-8704-4e4aa51006b4",
  "issued": "2021-08-31T00:00:00Z",
  "issuer": {
    "id": "did:key:z6MkrHKzgsahxBLyNAbLQyB1pcWNYC9GmywiWPgkrvntAZcj",
    "image": {
      "id": "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/1660296169313-K159K9WX8J8PPJE005HV/Walt+Bot_Logo.png?format\u003d100w",
      "type": "Image"
    },
    "name": "CH Authority",
    "type": "Profile",
    "url": "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/1660296169313-K159K9WX8J8PPJE005HV/Walt+Bot_Logo.png?format\u003d100w"
  },
  "validFrom": "2021-08-31T00:00:00Z",
  "issuanceDate": "2021-08-31T00:00:00Z"
}"""

    val TEST_SUBJECT_DID =
        "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5Iiwia2lkIjoiMW1lTUJuX3EtVklTQzd5Yk42UnExX0FISkxwSHZKVG83N3V6Nk44UkdDQSIsIngiOiJQdEV1YlB1MWlrRzR5emZsYUF2dnNmTWIwOXR3NzlIcTFsVnJRX1c0ZnVjIn0"

    @Language("JSON")
    val TEST_MAPPING = """{
  "id": "\u003cuuid\u003e",
  "issuer": {
    "id": "\u003cissuerDid\u003e"
  },
  "credentialSubject": {
    "id": "\u003csubjectDid\u003e"
  },
  "issuanceDate": "\u003ctimestamp\u003e",
  "expirationDate": "\u003ctimestamp-in:365d\u003e"
}"""


    @Language("JSON")
    val TEST_MAPPING_WITH_DISPLAY = """{
  "id": "\u003cuuid\u003e",
  "display": "\u003cdisplay\u003e",
  "issuer": {
    "id": "\u003cissuerDid\u003e"
  },
  "credentialSubject": {
    "id": "\u003csubjectDid\u003e"
  },
  "issuanceDate": "\u003ctimestamp\u003e",
  "expirationDate": "\u003ctimestamp-in:365d\u003e"
}"""

    val jsonKeyObj = Json.decodeFromString<JsonObject>(TEST_KEY)
    val jsonVCObj = Json.decodeFromString<JsonObject>(TEST_W3VC)
    val jsonMappingObj = Json.decodeFromString<JsonObject>(TEST_MAPPING)
    val jsonMappingObjWithDisplay = Json.decodeFromString<JsonObject>(TEST_MAPPING_WITH_DISPLAY)
}
    @Test
    fun testJwt() = runTest {
        val issueRequest =
            IssuanceRequest(
                issuerKey = jsonKeyObj,
                credentialData = jsonVCObj,
                credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
                mapping = jsonMappingObj,
                issuerDid = TEST_ISSUER_DID
            )

        ConfigManager.testWithConfigs(testConfigs)
        val offerUri = createCredentialOfferUri(listOf(issueRequest), CredentialFormat.jwt_vc_json)

        assertTrue {
            Url(offerUri).parameters["credential_offer_uri"]!!.contains("draft13")
        }
    }

    @Test
    fun testIssuanceWithCredentialDisplayMapping() = runTest {
        val issueRequest =
            IssuanceRequest(
                issuerKey = jsonKeyObj,
                credentialData = jsonVCObj,
                credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
                mapping = jsonMappingObjWithDisplay,
                issuerDid = TEST_ISSUER_DID
            )

        ConfigManager.testWithConfigs(testConfigs)
        val offerUri = createCredentialOfferUri(listOf(issueRequest), CredentialFormat.jwt_vc_json)

        assertTrue {
            Url(offerUri).parameters["credential_offer_uri"]!!.contains("draft13")
        }
    }


    @Test
    fun testSdJwt() = runTest {
        val jsonKeyObj = Json.decodeFromString<JsonObject>(TEST_KEY)
        val jsonVCObj = Json.decodeFromString<JsonObject>(TEST_W3VC)
        val jsonMappingObj = Json.decodeFromString<JsonObject>(TEST_MAPPING)

        val selectiveDisclosureMap = SDMapBuilder().addField("sd", true).build()
        val issueRequest = IssuanceRequest(
            issuerKey = jsonKeyObj,
            credentialData = jsonVCObj,
            credentialConfigurationId = "OpenBadgeCredential",
            mapping = jsonMappingObj,
            selectiveDisclosure = selectiveDisclosureMap,
            issuerDid = TEST_ISSUER_DID
        )

        ConfigManager.testWithConfigs(testConfigs)
        val offerUri = createCredentialOfferUri(listOf(issueRequest), CredentialFormat.jwt_vc_json)

        assertTrue {
            Url(offerUri).parameters["credential_offer_uri"]!!.contains("draft13")
        }
    }

    @Test
    fun testSign() = runTest {
        val key = KeyManager.resolveSerializedKey(TEST_KEY)
        val jsonVCObj = Json.decodeFromString<JsonObject>(TEST_W3VC)

        val subjectDid = TEST_SUBJECT_DID

        val w3cVc = W3CVC(jsonVCObj.toMap())

        val sign = w3cVc.signJws(
            issuerKey = key, issuerId = TEST_ISSUER_DID, subjectDid = subjectDid
        )

        assertEquals(true, sign.isNotEmpty())
    }

    @Test
    fun testBatchIssuanceJwt() = runTest {
        val jsonKeyObj = Json.decodeFromString<JsonObject>(TEST_KEY)
        val jsonVCObj1 = Json.decodeFromString<JsonObject>(TEST_W3VC)
        val jsonVCObj2 = Json.decodeFromString<JsonObject>(TEST_W3VC2)
        val jsonMappingObj = Json.decodeFromString<JsonObject>(TEST_MAPPING)

        val issueRequest1 = IssuanceRequest(
            issuerKey = jsonKeyObj,
            credentialData = jsonVCObj1,
            credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
            mapping = jsonMappingObj,
            issuerDid = TEST_ISSUER_DID,
        )
        val issueRequest2 = IssuanceRequest(
            issuerKey = jsonKeyObj,
            credentialConfigurationId = "BankId_jwt_vc_json",
            credentialData = jsonVCObj2,
            mapping = jsonMappingObj,
            issuerDid = TEST_ISSUER_DID,
        )

        val issuanceRequests = listOf(issueRequest1, issueRequest2)

        ConfigManager.loadConfigs(emptyArray())
        val offerUri = createCredentialOfferUri(issuanceRequests, CredentialFormat.jwt_vc_json)

        assertTrue {
            Url(offerUri).parameters["credential_offer_uri"]!!.contains("draft13")
        }

    }

    @Test
    fun testEventSerialization() {

        val json = Json.encodeToString(
            IssuanceEvent(
            "originator",
            organization = "organization",
            target = "target",
            timestamp = 0,
            status = Status("status"),
            action = Action("action"),
            sessionId = "sessionId",
            credentialConfigurationId = "credentialConfigurationId",
            format = "format",
            proofType = null,
            holderId = null,
            callId = null,
            error = null
        )
        )
        println(json)
    }
}

package id.walt

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun testUtilHttpClient(token: String? = null, doFollowRedirects: Boolean = true) = HttpClient() {
    install(ContentNegotiation) {
        json(
            Json {
                explicitNulls = false
                encodeDefaults = true
            }
        )
    }
    install(DefaultRequest) {
        contentType(ContentType.Application.Json)
        host = "127.0.0.1"
        port = 22222

        if (token != null) bearerAuth(token)
    }
    install(Logging) {
        level = LogLevel.ALL
    }
    followRedirects = doFollowRedirects
}

suspend fun getExampleCredentialOffer() {
    // Setup Issuer - Currently, the deployed one is used
    val DEPLOYED_ISSUER_BASE_URL = "https://issuer.portal.test.waltid.cloud"
    val ISSUER_DID_KEY = runBlocking { JWKKey.generate(KeyType.Ed25519) }
    val ISSUER_DID = runBlocking { DidService.registerByKey("jwk", ISSUER_DID_KEY).did }
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

    val httpClient = testUtilHttpClient()
    val createCredentialOfferRequestBody = buildJsonObject {
        put("issuerKey", buildJsonObject {
            put("type", "jwk")
            put("jwk", ISSUER_DID_KEY.exportJWKObject())
        }
        )
        put("issuerDid", ISSUER_DID)
        put("credentialConfigurationId", "OpenBadgeCredential_jwt_vc_json")
        put("credentialData", Json.parseToJsonElement(openBadgeCredentialData).jsonObject)
        put(
            "mapping", Json.parseToJsonElement(
                """
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
            ).jsonObject
        )
    }

    // Create Offer In Issuer API and get it as a String
    val credentialOfferUrlString = httpClient.post("${DEPLOYED_ISSUER_BASE_URL}/openid4vc/jwt/issue") {
        setBody(Json.encodeToJsonElement(createCredentialOfferRequestBody))
    }.body<String>()
}

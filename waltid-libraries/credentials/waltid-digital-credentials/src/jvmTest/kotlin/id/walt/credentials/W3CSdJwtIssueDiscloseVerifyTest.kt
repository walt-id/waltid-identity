package id.walt.credentials

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reproduces the enterprise W3C `university-degree-with-disclosures` present pipeline using the
 * legacy SD-JWT lib as the issuer (exactly how the OSS issuer produces the credential):
 *   issue (legacy SDPayload) -> wire SD-JWT -> CredentialParser.parse -> store round-trip
 *   -> disclose() -> legacy SDJwt.getFullPayload (the verifier call that throws
 *      "N disclosure(s) not referenced by any digest").
 */
class W3CSdJwtIssueDiscloseVerifyTest {

    private fun b64url(json: JsonElement) = json.toString().encodeToByteArray().encodeToBase64Url()

    @Test
    fun issuedW3CSdJwtRoundTripsThroughParseDiscloseVerify() = runTest {
        // Full vc payload with issuanceDate + credentialSubject.degree.name
        val fullPayload = buildJsonObject {
            put("iss", "did:example:issuer")
            put("vc", buildJsonObject {
                put("@context", buildJsonArray { add("https://www.w3.org/2018/credentials/v1") })
                put("type", buildJsonArray { add("VerifiableCredential"); add("UniversityDegreeCredential") })
                put("issuanceDate", "2024-01-01T00:00:00Z")
                put("credentialSubject", buildJsonObject {
                    put("id", "did:example:holder")
                    put("degree", buildJsonObject {
                        put("type", "BachelorDegree")
                        put("name", "Bachelor of Science")
                    })
                })
            })
        }

        // SD on vc.issuanceDate and vc.credentialSubject.degree.name (mirrors the integration test SDMap)
        val sdMap = SDMap(mapOf(
            "vc" to SDField(false, children = SDMap(mapOf(
                "issuanceDate" to SDField(true),
                "credentialSubject" to SDField(false, children = SDMap(mapOf(
                    "degree" to SDField(false, children = SDMap(mapOf(
                        "name" to SDField(true)
                    )))
                )))
            )))
        ))

        // Issue using the legacy lib (as the OSS issuer does).
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)
        val header = buildJsonObject { put("typ", "JWT"); put("alg", "ES256"); put("kid", "k") }
        val jwt = "${b64url(header)}.${b64url(sdPayload.undisclosedPayload)}.ZmFrZXNpZw"
        val wireSdJwt = "$jwt~${sdPayload.sDisclosures.joinToString("~") { it.disclosure }}~"

        // Sanity: the issuer's own output verifies.
        SDJwt.parse(wireSdJwt).fullPayload

        // Wallet receive: parse, then store round-trip (serialize/deserialize).
        val (_, parsed) = CredentialParser.detectAndParse(wireSdJwt)
        val json = Json { ignoreUnknownKeys = true }
        val vc = json.decodeFromString(
            DigitalCredential.serializer(),
            json.encodeToString(DigitalCredential.serializer(), parsed)
        )
        check(vc is SelectivelyDisclosableVerifiableCredential)
        assertEquals(2, vc.disclosures!!.size)

        // Present (disclose all) and verify with the legacy verifier.
        val disclosed = vc.disclose(vc, vc.disclosures!!)
        val full = SDJwt.parse(disclosed).fullPayload
        val vcOut = full["vc"]!!.jsonObject
        assertEquals("2024-01-01T00:00:00Z", vcOut["issuanceDate"]?.jsonPrimitive?.content)
        assertEquals(
            "Bachelor of Science",
            vcOut["credentialSubject"]!!.jsonObject["degree"]!!.jsonObject["name"]?.jsonPrimitive?.content
        )
    }
}

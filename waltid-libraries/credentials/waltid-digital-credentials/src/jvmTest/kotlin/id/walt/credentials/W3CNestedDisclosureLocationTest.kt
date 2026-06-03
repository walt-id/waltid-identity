package id.walt.credentials

import id.walt.credentials.formats.AbstractW3C
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
/**
 * Reproduces VerifierPresentCredentialsIntegrationTest.shouldIssueAndPresentUniversityDegreeWithDisclosures
 * at the unit level: a W3C v1.1 jwt_vc_json credential with a top-level selective disclosure
 * (issuanceDate) and a nested one (credentialSubject.degree.name).
 *
 * The enterprise test fails at `disclosure.location ?: throw`, so this asserts that every parsed
 * disclosure has a non-null [SdJwtSelectiveDisclosure.location].
 */
class W3CNestedDisclosureLocationTest {

    private fun b64url(json: JsonElement): String =
        json.toString().encodeToByteArray().encodeToBase64Url()

    /** Build a disclosure [salt, name, value] and its RFC 9901 digest (asHashed). */
    private fun makeDisclosure(salt: String, name: String, value: JsonElement): Pair<SdJwtSelectiveDisclosure, String> {
        val disc = SdJwtSelectiveDisclosure(salt = salt, name = name, value = value)
        return disc to disc.asHashed()
    }

    @Test
    fun w3cV11NestedDisclosuresGetLocationPopulated() = runTest {
        // 1. Build disclosures
        val (issuanceDateDisc, issuanceDateHash) =
            makeDisclosure("saltIssuanceDate", "issuanceDate", JsonPrimitive("2024-01-01T00:00:00Z"))
        val (degreeNameDisc, degreeNameHash) =
            makeDisclosure("saltDegreeName", "name", JsonPrimitive("Bachelor of Science"))

        // 2. Build the W3C v1.1 payload: issuanceDate SD at top-level vc._sd,
        //    degree.name SD at vc.credentialSubject.degree._sd (degree itself NOT selectively disclosed)
        val vc = buildJsonObject {
            put("@context", buildJsonArray { add("https://www.w3.org/2018/credentials/v1") })
            put("type", buildJsonArray { add("VerifiableCredential"); add("UniversityDegreeCredential") })
            put("issuer", "did:example:issuer")
            putJsonObject("credentialSubject") {
                put("id", "did:example:holder")
                putJsonObject("degree") {
                    put("type", "BachelorDegree")
                    putJsonArray("_sd") { add(degreeNameHash) }
                }
            }
            putJsonArray("_sd") { add(issuanceDateHash) }
        }

        val payload = buildJsonObject {
            put("iss", "did:example:issuer")
            put("sub", "did:example:holder")
            put("vc", vc)
        }
        val header = buildJsonObject {
            put("typ", "JWT")
            put("alg", "ES256")
            put("kid", "did:example:issuer#key-1")
        }

        // 3. Assemble compact SD-JWT VC: header.payload.signature~disc1~disc2~
        val signedJwt = "${b64url(header)}.${b64url(payload)}.ZmFrZXNpZw"
        val credential = "$signedJwt~${issuanceDateDisc.asEncoded()}~${degreeNameDisc.asEncoded()}~"

        // 4. Parse
        val (detection, parsed) = CredentialParser.detectAndParse(credential)
        println("Detected: $detection")

        assertTrue(parsed is AbstractW3C, "Expected an AbstractW3C credential, got ${parsed::class.simpleName}")
        val disclosures = assertNotNull(parsed.disclosures, "Parsed credential has no disclosures")
        assertEquals(2, disclosures.size, "Expected 2 disclosures")

        // 5. Assert: every disclosure has a non-null location (the enterprise test requirement)
        disclosures.forEach { disc ->
            assertNotNull(
                disc.location,
                "Disclosure '${disc.name}' has null location (would fail VerifierPresentCredentialsIntegrationTest)"
            )
        }

        // 6. Assert the locations are spec-compliant Claim Paths (SD-JWT VC §4.6.1):
        //    arrays of string components, relative to the credential root (vc wrapper dropped).
        val byName = disclosures.associateBy { it.name }
        val issuanceLoc = assertNotNull(byName["issuanceDate"]?.location)
        val nameLoc = assertNotNull(byName["name"]?.location)

        assertEquals(listOf(JsonPrimitive("issuanceDate")), issuanceLoc)
        assertEquals(
            listOf(JsonPrimitive("credentialSubject"), JsonPrimitive("degree"), JsonPrimitive("name")),
            nameLoc
        )
    }
}

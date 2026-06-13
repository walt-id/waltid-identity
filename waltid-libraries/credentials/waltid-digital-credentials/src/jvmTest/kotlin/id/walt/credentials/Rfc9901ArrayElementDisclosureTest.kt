package id.walt.credentials

import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for the RFC 9901 array-element selective disclosure reachability fix.
 *
 * Reproduces the structure of NOW's SJV-EAA-9 / SJV-EAA-10 (ETSI plugtest) where our verifier
 * previously wrongly rejected credentials as having "unreachable" disclosures:
 *
 *   "Invalid disclosures: N disclosures provided but only M are reachable from the payload hashes."
 *
 * Root cause (fixed): the initial reachability seeding only scanned `_sd` object arrays at the
 * payload root, never array-element `{ "...": <digest> }` objects (RFC 9901 §6) located directly
 * in the payload. RFC 9901 §7.1 step 3.b requires identifying BOTH `_sd` arrays AND array-element
 * digests across the WHOLE payload (recursively). The fix seeds reachability via scanForHashes over
 * the credential-root payload.
 */
class Rfc9901ArrayElementDisclosureTest {

    private fun b64url(json: JsonElement): String =
        json.toString().encodeToByteArray().encodeToBase64Url()

    /** Build an ARRAY-ELEMENT disclosure [salt, value] (name == null) and its RFC 9901 digest. */
    private fun makeArrayDisclosure(salt: String, value: JsonElement): Pair<SdJwtSelectiveDisclosure, String> {
        val disc = SdJwtSelectiveDisclosure(salt = salt, name = null, value = value)
        return disc to disc.asHashed()
    }

    /** Build an OBJECT-PROPERTY disclosure [salt, name, value] and its RFC 9901 digest. */
    private fun makeObjectDisclosure(salt: String, name: String, value: JsonElement): Pair<SdJwtSelectiveDisclosure, String> {
        val disc = SdJwtSelectiveDisclosure(salt = salt, name = name, value = value)
        return disc to disc.asHashed()
    }

    /**
     * NOW SJV-EAA-9 shape: a top-level disclosable array `pets` whose elements are array-element
     * disclosures `{ "...": digest }`, and NO `_sd` object array at all.
     * Before the fix: 0/2 reachable -> rejected. After: both reachable -> parses.
     */
    @Test
    fun topLevelArrayElementDisclosuresAreReachable() = runTest {
        val (catDisc, catHash) = makeArrayDisclosure("6S2RFDoR7lmMQ4_GRcv6Vw", JsonPrimitive("cat"))
        val (dogDisc, dogHash) = makeArrayDisclosure("wBhOIPp2JSllLbo8ArI1Zw", JsonPrimitive("dog"))

        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", "urn:etsi:eaa:credential")
            putJsonArray("pets") {
                add(buildJsonObject { put("...", catHash) })
                add(buildJsonObject { put("...", dogHash) })
            }
        }
        val header = buildJsonObject {
            put("typ", "dc+sd-jwt")
            put("alg", "ES256")
        }

        val signedJwt = "${b64url(header)}.${b64url(payload)}.ZmFrZXNpZw"
        val credential = "$signedJwt~${catDisc.asEncoded()}~${dogDisc.asEncoded()}~"

        // Must NOT throw UnreachableDisclosuresException (was the bug).
        val (_, parsed) = CredentialParser.detectAndParse(credential)
        assertTrue(parsed is SelectivelyDisclosableVerifiableCredential, "Expected SD-JWT VC, got ${parsed::class.simpleName}")
        val disclosures = assertNotNull(parsed.disclosures, "Parsed credential has no disclosures")
        assertEquals(2, disclosures.size, "Both array-element disclosures must be reachable (was 0/2 before fix)")
    }

    /**
     * NOW SJV-EAA-10 shape: MIX of object-property `_sd` disclosures AND top-level array-element
     * disclosures. Before the fix: only the `_sd` ones were reachable (e.g. 5/7) -> rejected.
     */
    @Test
    fun mixedObjectAndArrayElementDisclosuresAllReachable() = runTest {
        val (familyNameDisc, familyNameHash) = makeObjectDisclosure("s1", "family_name", JsonPrimitive("Mustermann"))
        val (givenNameDisc, givenNameHash) = makeObjectDisclosure("s2", "given_name", JsonPrimitive("Max"))
        val (catDisc, catHash) = makeArrayDisclosure("s3", JsonPrimitive("cat"))
        val (dogDisc, dogHash) = makeArrayDisclosure("s4", JsonPrimitive("dog"))

        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", "urn:etsi:eaa:credential")
            putJsonArray("_sd") { add(familyNameHash); add(givenNameHash) }
            putJsonArray("pets") {
                add(buildJsonObject { put("...", catHash) })
                add(buildJsonObject { put("...", dogHash) })
            }
        }
        val header = buildJsonObject {
            put("typ", "dc+sd-jwt")
            put("alg", "ES256")
        }

        val signedJwt = "${b64url(header)}.${b64url(payload)}.ZmFrZXNpZw"
        val credential =
            "$signedJwt~${familyNameDisc.asEncoded()}~${givenNameDisc.asEncoded()}~${catDisc.asEncoded()}~${dogDisc.asEncoded()}~"

        val (_, parsed) = CredentialParser.detectAndParse(credential)
        assertTrue(parsed is SelectivelyDisclosableVerifiableCredential, "Expected SD-JWT VC, got ${parsed::class.simpleName}")
        val disclosures = assertNotNull(parsed.disclosures, "Parsed credential has no disclosures")
        assertEquals(4, disclosures.size, "All 4 (2 object + 2 array-element) disclosures must be reachable")
    }

    /**
     * Negative control: a genuinely unreferenced disclosure (no matching digest in the payload)
     * MUST still cause rejection per RFC 9901 §7.1 step 5. Ensures the fix did not over-relax.
     */
    @Test
    fun unreferencedArrayElementDisclosureStillRejected() = runTest {
        val (catDisc, catHash) = makeArrayDisclosure("s1", JsonPrimitive("cat"))
        val (orphanDisc, _) = makeArrayDisclosure("s2", JsonPrimitive("orphan")) // digest NOT in payload

        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", "urn:etsi:eaa:credential")
            putJsonArray("pets") { add(buildJsonObject { put("...", catHash) }) }
        }
        val header = buildJsonObject {
            put("typ", "dc+sd-jwt")
            put("alg", "ES256")
        }
        val signedJwt = "${b64url(header)}.${b64url(payload)}.ZmFrZXNpZw"
        val credential = "$signedJwt~${catDisc.asEncoded()}~${orphanDisc.asEncoded()}~"

        val result = runCatching { CredentialParser.detectAndParse(credential) }
        assertTrue(
            result.isFailure,
            "An unreferenced disclosure must cause rejection per RFC 9901 §7.1 step 5"
        )
    }
}

package id.walt.credentials

import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that [CredentialParser] accepts SD-JWT VCs encoded using the JWS JSON Serialization
 * (RFC 9901 §8) — both the Flattened and the General form. This format is valid but OPTIONAL
 * per SD-JWT VC draft §2.2; a conformant verifier should accept it.
 *
 * Strategy: take a known-good COMPACT SD-JWT VC, convert it to each JWS JSON form, and assert the
 * parser produces an equivalent SD-JWT VC credential (same detection type, claims and disclosures).
 */
class SdJwtVcJwsJsonSerializationTest {

    private fun b64u(s: String): String =
        s.encodeToByteArray().let { kotlin.io.encoding.Base64.UrlSafe.encode(it).trimEnd('=') }

    /** Split a compact SD-JWT "header.payload.sig~d1~d2~[kb]" into its parts. */
    private data class CompactParts(
        val protected: String, val payload: String, val signature: String,
        val disclosures: List<String>, val kbJwt: String?,
    )

    private fun splitCompact(compact: String): CompactParts {
        val segments = compact.split("~")
        val jwt = segments.first()
        val (p, pl, s) = jwt.split(".")
        // Trailing "~" yields an empty last element; a KB-JWT (contains ".") would be the last non-empty.
        val rest = segments.drop(1)
        val kb = rest.lastOrNull { it.isNotEmpty() && it.contains(".") }
        val disclosures = rest.filter { it.isNotEmpty() && it != kb }
        return CompactParts(p, pl, s, disclosures, kb)
    }

    private fun toFlattenedJwsJson(compact: String): String {
        val c = splitCompact(compact)
        return buildJsonObject {
            put("payload", c.payload)
            put("protected", c.protected)
            putJsonObject("header") {
                putJsonArray("disclosures") { c.disclosures.forEach { add(it) } }
                c.kbJwt?.let { put("kb_jwt", it) }
            }
            put("signature", c.signature)
        }.toString()
    }

    private fun toGeneralJwsJson(compact: String): String {
        val c = splitCompact(compact)
        return buildJsonObject {
            put("payload", c.payload)
            putJsonArray("signatures") {
                add(buildJsonObject {
                    put("protected", c.protected)
                    putJsonObject("header") {
                        putJsonArray("disclosures") { c.disclosures.forEach { add(it) } }
                        c.kbJwt?.let { put("kb_jwt", it) }
                    }
                    put("signature", c.signature)
                })
            }
        }.toString()
    }

    private suspend fun assertParsesLikeCompact(jwsJson: String, compact: String) {
        val (jwsDetection, jwsCred) = CredentialParser.detectAndParse(jwsJson)
        val (compactDetection, compactCred) = CredentialParser.detectAndParse(compact)

        assertEquals(compactDetection.credentialPrimaryType, jwsDetection.credentialPrimaryType)
        assertEquals(compactDetection.credentialSubType, jwsDetection.credentialSubType)

        assertTrue(jwsCred is SelectivelyDisclosableVerifiableCredential, "must parse as SD-JWT VC")
        check(compactCred is SelectivelyDisclosableVerifiableCredential)

        // Same resolved claims and same number of disclosures.
        assertEquals(compactCred.credentialData, jwsCred.credentialData, "resolved claims must match compact form")
        assertEquals(
            compactCred.disclosures?.size ?: 0,
            jwsCred.disclosures?.size ?: 0,
            "disclosure count must match compact form"
        )
    }

    @Test
    fun flattenedJwsJsonParsesAsSdJwtVc() = runTest {
        val compact = SdJwtExamples.sdJwtVcSignedExample2
        assertParsesLikeCompact(toFlattenedJwsJson(compact), compact)
    }

    @Test
    fun generalJwsJsonParsesAsSdJwtVc() = runTest {
        val compact = SdJwtExamples.sdJwtVcSignedExample2
        assertParsesLikeCompact(toGeneralJwsJson(compact), compact)
    }
}

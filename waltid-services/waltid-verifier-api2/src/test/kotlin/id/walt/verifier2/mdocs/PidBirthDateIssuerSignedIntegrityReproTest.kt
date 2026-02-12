package id.walt.verifier2.mdocs

import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.parser.MdocParser
import id.walt.policies2.vp.policies.IssuerSignedDataMdocVpPolicy
import id.walt.policies2.vp.policies.VerificationSessionContext
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PidBirthDateIssuerSignedIntegrityReproTest {

    @Test
    fun issuerSignedIntegrity_requires_pid_birthDate_fullDate_encoding() = kotlinx.coroutines.test.runTest {
        /**
         * This test is a regression guard for a real-world mdoc PID (`eu.europa.ec.eudi.pid.1`) sample
         * that failed issuer-signed digest integrity verification in Verifier2.
         *
         * Correct encoding (normative):
         * - `birth_date` is a `full-date` (RFC 3339 "full-date") and must be encoded as CBOR tag 1004:
         *   `full-date = #6.1004(tstr)` (RFC 8943).
         *   RFC 8943 (CBOR tags for date/time): https://www.rfc-editor.org/rfc/rfc8943
         *
         * - The EUDI PID Rulebook (Annex 3.01) defines the PID data elements and their ISO/IEC 18013-5 encodings.
         *   PID Rulebook: https://eudi.dev/2.4.0/annexes/annex-3/annex-3.01-pid-rulebook/
         *
         * What goes wrong when the encoding is not preserved:
         * - If the `birth_date` value is decoded into a plain `String` (losing the CBOR tag information),
         *   re-serialization during digest computation can differ from the original IssuerSignedItemBytes,
         *   leading to a digest mismatch and failing `mso_mdoc/issuer_signed_integrity`.
         */
        val signed = loadResource("fixtures/pid_birth_date_device_response.b64url")

        val document = MdocParser.parseToDocument(signed)
        val mso = document.issuerSigned.decodeMobileSecurityObject()

        val namespace = "eu.europa.ec.eudi.pid.1"
        val elementId = "birth_date"

        val issuerSignedList = document.issuerSigned.namespaces?.get(namespace)
        assertNotNull(issuerSignedList, "Expected issuerSigned namespace $namespace in fixture")

        val wrapped = issuerSignedList.entries.firstOrNull { it.value.elementIdentifier == elementId }
        assertNotNull(wrapped, "Expected issuerSigned element $elementId in fixture")

        assertTrue(
            wrapped.serialized.containsSequence(byteArrayOf(0xD9.toByte(), 0x03, 0xEC.toByte())),
            "Fixture bytes should contain CBOR tag 1004 (0x03EC) for full-date"
        )

        // PID `birth_date` is a `full-date` (CBOR tag 1004) and should be modeled as a date type.
        // If this is a String, the implementation is very likely losing tagged-CBOR semantics.
        assertTrue(
            wrapped.value.elementValue is LocalDate,
            "PID birth_date must be handled as LocalDate (CBOR full-date tag 1004), but was ${wrapped.value.elementValue::class.simpleName}. " +
                "If this is String, tagged-CBOR semantics are likely lost, causing digest mismatch."
        )

        val digestFromMso = mso.valueDigests[namespace]
            ?.entries
            ?.firstOrNull { it.key == wrapped.value.digestId }
        assertNotNull(digestFromMso, "Expected MSO valueDigest for digestId=${wrapped.value.digestId} in namespace $namespace")

        val recomputedDigest = ValueDigest.fromIssuerSignedItemBytes(wrapped.value.digestId, wrapped.serialized, mso.digestAlgorithm).value
        assertTrue(digestFromMso.value.contentEquals(recomputedDigest), "Expected digest match for correctly handled full-date encoding")

        val result = IssuerSignedDataMdocVpPolicy().runPolicy(document, mso, dummyVerificationContext())

        assertTrue(result.success)
        assertTrue(result.errors.isEmpty())
    }

    private fun loadResource(path: String): String {
        val url = checkNotNull(javaClass.classLoader.getResource(path)) { "Missing test resource: $path" }
        return url.readText().trim()
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
        if (sequence.isEmpty() || this.size < sequence.size) return false
        for (startIndex in 0..(this.size - sequence.size)) {
            var matches = true
            for (sequenceIndex in sequence.indices) {
                if (this[startIndex + sequenceIndex] != sequence[sequenceIndex]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    private fun dummyVerificationContext() = VerificationSessionContext(
        vpToken = "vp_token",
        expectedNonce = "nonce",
        expectedAudience = null,
        expectedOrigins = null,
        responseUri = null,
        responseMode = OpenID4VPResponseMode.DIRECT_POST,
        isSigned = true,
        isEncrypted = false,
        jwkThumbprint = null,
        isAnnexC = false
    )
}

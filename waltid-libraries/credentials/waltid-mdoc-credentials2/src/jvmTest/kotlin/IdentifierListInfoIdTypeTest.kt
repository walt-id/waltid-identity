@file:OptIn(ExperimentalSerializationApi::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.mso.Status
import id.walt.mdoc.objects.mso.Status.IdentifierListInfo
import id.walt.mdoc.objects.mso.UniformResourceIdentifier
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for the ISO/IEC 18013-5 §9.1.2.6 identifier-list `id` type.
 *
 * Per ISO 18013-5 §9.1.2.6:  IdentifierListInfo = { "id": Identifier, "uri": URI, ? "certificate": Certificate }
 * with  Identifier = bstr  (a CBOR byte string).
 *
 * Background (ETSI plugtest): NOW's MDL-EAA-9 / MDOC-EAA-11 encode `id` as a CBOR *integer* (0x01),
 * which is NOT conformant. Our verifier previously typed `id` as a String and failed with
 * "Expected start of string, but found 01"; the model now correctly types `id` as a byte string
 * (bstr), so it accepts spec-compliant credentials and still rejects the non-compliant integer form.
 */
class IdentifierListInfoIdTypeTest {

    @Test
    fun byteStringIdRoundTrips() {
        val info = IdentifierListInfo(
            id = byteArrayOf(0x01, 0x02, 0x03, 0x04),
            uri = UniformResourceIdentifier("https://issuer.example/identifier-list.cwt"),
        )
        val status = Status(identifierList = info)

        val bytes = coseCompliantCbor.encodeToByteArray(status)
        val decoded = coseCompliantCbor.decodeFromByteArray<Status>(bytes)

        val decodedInfo = assertNotNull(decoded.identifierList, "identifier_list must round-trip")
        assertTrue(
            byteArrayOf(0x01, 0x02, 0x03, 0x04).contentEquals(decodedInfo.id),
            "id (bstr) must round-trip unchanged"
        )
        assertEquals("https://issuer.example/identifier-list.cwt", decodedInfo.uri.string)
    }

    @Test
    fun optionalCertificateRoundTrips() {
        val info = IdentifierListInfo(
            id = byteArrayOf(0x0A),
            uri = UniformResourceIdentifier("https://issuer.example/list.cwt"),
            certificate = byteArrayOf(0x30, 0x82.toByte(), 0x01),  // DER-ish placeholder
        )
        val status = Status(identifierList = info)
        val decoded = coseCompliantCbor.decodeFromByteArray<Status>(coseCompliantCbor.encodeToByteArray(status))
        val cert = assertNotNull(decoded.identifierList?.certificate, "optional certificate (bstr) must round-trip")
        assertTrue(byteArrayOf(0x30, 0x82.toByte(), 0x01).contentEquals(cert))
    }

    /**
     * NOW's non-compliant encoding: identifier_list with an INTEGER id (CBOR 0x01) instead of a bstr.
     * Decoding MUST fail (we correctly reject non-conformant credentials rather than silently accept).
     *
     * Hand-built CBOR:
     *   a1                          # map(1)  (Status)
     *     6f identifier_list        # "identifier_list"
     *     a2                        # map(2)  (IdentifierListInfo)
     *       62 6964                 # "id"
     *       01                      # unsigned(1)  <-- non-compliant integer (should be bstr)
     *       63 757269               # "uri"
     *       6c "https://x/y"        # text
     */
    @Test
    fun integerIdIsRejected() {
        val uri = "https://x/y.cwt"
        val out = ArrayList<Byte>()
        out.add(0xA1.toByte())                                  // map(1)
        // key "identifier_list"
        val k1 = "identifier_list".encodeToByteArray()
        out.add((0x60 or k1.size).toByte()); out.addAll(k1.toList())
        out.add(0xA2.toByte())                                  // map(2)
        // "id" -> integer 1 (non-compliant)
        val kId = "id".encodeToByteArray()
        out.add((0x60 or kId.size).toByte()); out.addAll(kId.toList())
        out.add(0x01)                                           // unsigned(1)
        // "uri" -> text string
        val kUri = "uri".encodeToByteArray()
        out.add((0x60 or kUri.size).toByte()); out.addAll(kUri.toList())
        val uriBytes = uri.encodeToByteArray()
        out.add((0x60 or uriBytes.size).toByte()); out.addAll(uriBytes.toList())

        val nonCompliant = out.toByteArray()

        val result = runCatching { coseCompliantCbor.decodeFromByteArray<Status>(nonCompliant) }
        assertTrue(
            result.isFailure,
            "An integer `id` violates ISO 18013-5 §9.1.2.6 (Identifier = bstr) and must be rejected"
        )
    }
}

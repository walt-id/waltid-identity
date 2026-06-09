package id.walt.etsi.generator

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.etsi.TestCase
import id.walt.etsi.TestCaseSection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for ETSI plugtest mdoc (MDL) generator test-data fixes (FIX 5):
 *  - The MDL namespace builder previously clobbered birth_date / expiry_date to "2024-01-01"
 *    (a buggy extra-items loop), making issue_date == expiry_date (BOSA: "issue_date shall be
 *    strictly before expiry_date") and birth_date wrong. Fixed via a base-keys guard.
 *  - `portrait` was a text string; ISO/IEC 18013-5 §7.2.1 requires a byte string (bstr). Now
 *    encoded as a CBOR byte string by the value-mapping function.
 */
class MdocGeneratorMdlFixTest {

    private fun loadResource(path: String): String =
        requireNotNull(this::class.java.classLoader.getResource(path)) { "Missing test resource: $path" }
            .readText()

    private val issuerKey: JWKKey by lazy {
        runBlocking { KeyManager.resolveSerializedKey(loadResource("testgen/key.jwk")) as JWKKey }
    }
    private val issuerCertPem: String by lazy { loadResource("testgen/cert.pem") }

    private fun mdlTestCase() = TestCase(
        id = "MDL-EAA-1",
        name = "MDL-EAA-1",
        subtitle = "",
        description = "mDL EAA test case",
        sections = listOf(TestCaseSection(title = "NameSpace", items = listOf("org.iso.18013.5.1")))
    )

    /** elementIdentifier -> elementValue (CborElement) for the ISO 18013-5 mDL namespace. */
    private fun mdlElements(): Map<String, Any> {
        val result = runBlocking {
            MdocGenerator.generate(
                testCase = mdlTestCase(),
                issuerKey = issuerKey,
                issuerCertificatePem = issuerCertPem,
                holderKey = JWKKey.generate(id.walt.crypto.keys.KeyType.secp256r1),
            )
        }
        val ns = assertNotNull(result.issuerSigned.namespaces?.get("org.iso.18013.5.1"), "mDL namespace missing")
        return ns.entries.associate { wrapper -> wrapper.value.elementIdentifier to (wrapper.value.elementValue as Any) }
    }

    @Test
    fun mdlIssueDateStrictlyBeforeExpiryDate() {
        val elems = mdlElements()
        val issue = assertNotNull(elems["issue_date"] as? CborString, "issue_date must be a (tagged full-date) string")
        val expiry = assertNotNull(elems["expiry_date"] as? CborString, "expiry_date must be a (tagged full-date) string")
        // FIX 5: must not be equal (BOSA "issue_date shall be strictly before expiry_date").
        assertNotEquals(
            issue.value, expiry.value,
            "issue_date (${issue.value}) must be strictly before expiry_date (${expiry.value})"
        )
        assertTrue(issue.value < expiry.value, "issue_date must be < expiry_date")
    }

    @Test
    fun mdlBirthDateNotClobbered() {
        val elems = mdlElements()
        val birth = assertNotNull(elems["birth_date"] as? CborString, "birth_date must be a (tagged full-date) string")
        // FIX 5: birth_date must remain its real value, not be clobbered to 2024-01-01.
        assertNotEquals("2024-01-01", birth.value, "birth_date must not be clobbered to the issue_date placeholder")
    }

    @Test
    fun mdlPortraitIsByteString() {
        val elems = mdlElements()
        val portrait = elems["portrait"]
        assertTrue(
            portrait is CborByteString,
            "portrait must be a CBOR byte string (bstr) per ISO 18013-5 §7.2.1, got: ${portrait?.let { it::class.simpleName }}"
        )
    }
}

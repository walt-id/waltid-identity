package id.walt.x509

import okio.ByteString.Companion.toByteString
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class X509Test {

    fun parseX5cBase64(x5cBase64: List<String>): List<CertificateDer> =
        x5cBase64.map {
            CertificateDer(Base64.getDecoder().decode(it).toByteString())
        }

    fun der(cert: X509Certificate) = CertificateDer(cert.encoded.toByteString())
    fun b64(cert: X509Certificate): String = Base64.getEncoder().encodeToString(cert.encoded)

    @Test
    fun `Should validate chain with provided trust-store`() {
        val c = TestCA.generateChain()

        // x5c typically includes leaf first; order doesn't matter to our validator
        val x5cBase64 = listOf(
            b64(c.leafCert),
            b64(c.interCert)
        )
        val parsed = parseX5cBase64(x5cBase64)

        // Build a truststore with only the root
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("root", c.rootCert)
        }
        val anchors = loadTrustAnchorsFromKeyStore(ks)

        // Should validate
        validateCertificateChain(
            leaf = der(c.leafCert),
            chain = parsed,
            trustAnchors = anchors
        )
    }

    @Test
    fun `Should validate chain with root included in X5C, no external anchors`() {
        val c = TestCA.generateChain()

        // Include root inside x5c (some issuers do this)
        val x5cBase64 = listOf(
            b64(c.leafCert),
            b64(c.interCert),
            b64(c.rootCert)
        )
        val parsed = parseX5cBase64(x5cBase64)

        // No explicit trust anchors; validator will pick the self-signed root in chain
        validateCertificateChain(
            leaf = der(c.leafCert),
            chain = parsed,
            enableTrustedChainRoot = true
        )
    }

    @Test
    fun `Should fail when wrong root is provided`() {
        val c = TestCA.generateChain()
        val other = TestCA.generateChain() // unrelated root

        val x5c = parseX5cBase64(
            listOf(b64(c.leafCert), b64(c.interCert))
        )
        val wrongAnchors = listOf(der(other.rootCert))

        val ex = assertFailsWith<X509ValidationException> {
            validateCertificateChain(
                leaf = der(c.leafCert),
                chain = x5c,
                trustAnchors = wrongAnchors
            )
        }
        assertTrue(ex.message!!.contains("path"), "Expected path build/validation failure")
    }

    @Test
    fun `Should fail when leaf expired`() {
        val now = System.currentTimeMillis()
        val past = java.util.Date(now - 10_000)
        val past2 = java.util.Date(now - 5_000)

        // Build a chain where the leaf is already expired
        val chainPast = TestCA.generateChain(notBefore = past, notAfter = past2)

        val x5c = parseX5cBase64(
            listOf(b64(chainPast.leafCert), b64(chainPast.interCert))
        )
        val anchors = listOf(der(chainPast.rootCert))

        val ex = assertFailsWith<X509ValidationException> {
            validateCertificateChain(
                leaf = der(chainPast.leafCert),
                chain = x5c,
                trustAnchors = anchors
            )
        }
        assertTrue(ex.message!!.contains("path"), "Expected path build/expiration failure")
    }

    @Test
    fun `Should validate that cert-order does not matter`() {
        val c = TestCA.generateChain()

        // Shuffle the x5c order
        val x5cBase64 = listOf(
            b64(c.interCert),
            b64(c.leafCert)
        )
        val parsed = parseX5cBase64(x5cBase64)
        val anchors = listOf(der(c.rootCert))

        validateCertificateChain(
            leaf = der(c.leafCert),
            chain = parsed,
            trustAnchors = anchors
        )
    }

    @Test
    fun `Should parse X5C Base64 certs`() {
        val c = TestCA.generateChain()
        val arr = listOf(b64(c.leafCert), b64(c.interCert), b64(c.rootCert))
        val parsed = parseX5cBase64(arr)
        assertEquals(3, parsed.size)
        assertTrue(parsed.all { it.bytes.size != 0 })
    }
}

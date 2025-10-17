package id.walt.x509

import java.security.KeyStore
import kotlin.test.*

class X509Test {

    @Test
    fun validChain_withProvidedTrustStore() {
        val c = CertChainGenerator.generateChain()

        // x5c typically includes leaf first; order doesn't matter to our validator
        val x5cBase64 = listOf(
            CertChainGenerator.b64(c.leafCert),
            CertChainGenerator.b64(c.interCert)
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
            leaf = CertChainGenerator.der(c.leafCert),
            chain = parsed,
            trustAnchors = anchors,
            enableRevocation = false
        )
    }

    @Test
    fun validChain_withRootIncludedInX5c_noExternalAnchors() {
        val c = CertChainGenerator.generateChain()

        // Include root inside x5c (some issuers do this)
        val x5cBase64 = listOf(
            CertChainGenerator.b64(c.leafCert),
            CertChainGenerator.b64(c.interCert),
            CertChainGenerator.b64(c.rootCert)
        )
        val parsed = parseX5cBase64(x5cBase64)

        // No explicit trust anchors; validator will pick the self-signed root in chain
        validateCertificateChain(
            leaf = CertChainGenerator.der(c.leafCert),
            chain = parsed,
            trustAnchors = null,
            enableRevocation = false
        )
    }

    @Test
    fun invalid_whenWrongRootProvided() {
        val c = CertChainGenerator.generateChain()
        val other = CertChainGenerator.generateChain() // unrelated root

        val x5c = parseX5cBase64(
            listOf(CertChainGenerator.b64(c.leafCert), CertChainGenerator.b64(c.interCert))
        )
        val wrongAnchors = listOf(CertChainGenerator.der(other.rootCert))

        val ex = assertFailsWith<X509ValidationException> {
            validateCertificateChain(
                leaf = CertChainGenerator.der(c.leafCert),
                chain = x5c,
                trustAnchors = wrongAnchors,
                enableRevocation = false
            )
        }
        assertTrue(ex.message!!.contains("path"), "Expected path build/validation failure")
    }

    @Test
    fun invalid_whenLeafExpired() {
        val now = System.currentTimeMillis()
        val past = java.util.Date(now - 10_000)
        val past2 = java.util.Date(now - 5_000)

        // Build a chain where the leaf is already expired
        val chainPast = CertChainGenerator.generateChain(notBefore = past, notAfter = past2)

        val x5c = parseX5cBase64(
            listOf(CertChainGenerator.b64(chainPast.leafCert), CertChainGenerator.b64(chainPast.interCert))
        )
        val anchors = listOf(CertChainGenerator.der(chainPast.rootCert))

        val ex = assertFailsWith<X509ValidationException> {
            validateCertificateChain(
                leaf = CertChainGenerator.der(chainPast.leafCert),
                chain = x5c,
                trustAnchors = anchors,
                enableRevocation = false
            )
        }
        // Message may vary, just assert it's a validation failure
        assertTrue(ex.message!!.contains("invalid", ignoreCase = true) || ex.message!!.contains("expired", ignoreCase = true))
    }

    @Test
    fun orderDoesNotMatter() {
        val c = CertChainGenerator.generateChain()

        // Shuffle the x5c order
        val x5cBase64 = listOf(
            CertChainGenerator.b64(c.interCert),
            CertChainGenerator.b64(c.leafCert)
        )
        val parsed = parseX5cBase64(x5cBase64)
        val anchors = listOf(CertChainGenerator.der(c.rootCert))

        validateCertificateChain(
            leaf = CertChainGenerator.der(c.leafCert),
            chain = parsed,
            trustAnchors = anchors,
            enableRevocation = false
        )
    }

    @Test
    fun parseX5cBase64_parsesAll() {
        val c = CertChainGenerator.generateChain()
        val arr = listOf(CertChainGenerator.b64(c.leafCert), CertChainGenerator.b64(c.interCert), CertChainGenerator.b64(c.rootCert))
        val parsed = parseX5cBase64(arr)
        assertEquals(3, parsed.size)
        assertTrue(parsed.all { it.bytes.isNotEmpty() })
    }
}

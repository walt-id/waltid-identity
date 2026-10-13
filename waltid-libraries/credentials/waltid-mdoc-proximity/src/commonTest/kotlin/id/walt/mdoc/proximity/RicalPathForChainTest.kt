package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class RicalPathForChainTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
    private val signUsages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)

    private class Pki(
        val root: X509Certificate,
        val intermediate: X509Certificate,
        val leaf: X509Certificate,
    )

    private suspend fun pki(name: String): Pki {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val rootKey = runtime.generateMdocTestKey("$name-root", signUsages)
        val intermediateKey = runtime.generateMdocTestKey("$name-intermediate", signUsages)
        val leafKey = runtime.generateMdocTestKey("$name-leaf", signUsages)
        val root = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=$name Root, O=Walt.id"
        }
        val intermediate = X509CertificateUtil.createCertificate(rootKey, root, sigAlg) {
            subjectDn = "CN=$name Intermediate, O=Walt.id"
            subjectPublicKey(intermediateKey)
            extensionBasicConstraints { cA = true }
        }
        val leaf = X509CertificateUtil.createCertificate(intermediateKey, intermediate, sigAlg) {
            subjectDn = "CN=$name Reader, O=Walt.id"
            subjectPublicKey(leafKey)
        }
        return Pki(root, intermediate, leaf)
    }

    private fun info(certificate: X509Certificate, ski: Byte, aki: Byte?, isTrustAnchor: Boolean) =
        RicalCertificateInfo(
            certificateDer = certificate.encodedDer,
            serialNumber = ByteString(byteArrayOf(ski)),
            subjectKeyIdentifier = ByteString(byteArrayOf(ski)),
            isTrustAnchor = isTrustAnchor,
            authorityKeyIdentifier = aki?.let { ByteString(byteArrayOf(it)) },
        )

    private fun rical(vararg infos: RicalCertificateInfo) = Rical(
        version = "1.0",
        provider = "provider",
        date = Instant.parse("2026-01-01T00:00:00Z"),
        certificateInfos = infos.toList(),
        type = "reader",
    )

    private fun ricalOf(pki: Pki, intermediateIsAnchor: Boolean = false) = rical(
        info(pki.root, 1, null, true),
        info(pki.intermediate, 2, 1, intermediateIsAnchor),
    )

    @Test
    fun `completes reader leaf with RICAL intermediate and anchor`() = runTest {
        val pki = pki("complete")
        val (path, anchor) = ricalOf(pki).pathForChain(listOf(pki.leaf))
        assertEquals(listOf(pki.leaf, pki.intermediate), path)
        assertEquals(pki.root, anchor)
    }

    @Test
    fun `accepts unordered reader chain`() = runTest {
        val pki = pki("unordered")
        val (path, anchor) = ricalOf(pki).pathForChain(listOf(pki.intermediate, pki.leaf))
        assertEquals(listOf(pki.leaf, pki.intermediate), path)
        assertEquals(pki.root, anchor)
    }

    @Test
    fun `excludes anchor already provided by the reader from the path`() = runTest {
        val pki = pki("with-root")
        val (path, anchor) = ricalOf(pki).pathForChain(listOf(pki.root, pki.intermediate, pki.leaf))
        assertEquals(listOf(pki.leaf, pki.intermediate), path)
        assertEquals(pki.root, anchor)
    }

    @Test
    fun `selects the highest trust anchor when an intermediate is also an anchor`() = runTest {
        val pki = pki("highest")
        val (path, anchor) = ricalOf(pki, intermediateIsAnchor = true).pathForChain(listOf(pki.leaf))
        assertEquals(listOf(pki.leaf, pki.intermediate), path)
        assertEquals(pki.root, anchor)
    }

    @Test
    fun `uses the anchor itself when the reader chain is only the anchor's direct child`() = runTest {
        val pki = pki("direct")
        val rical = rical(info(pki.intermediate, 2, null, true))
        val (path, anchor) = rical.pathForChain(listOf(pki.leaf))
        assertEquals(listOf(pki.leaf), path)
        assertEquals(pki.intermediate, anchor)
    }

    @Test
    fun `rejects empty reader chain`() = runTest {
        val pki = pki("empty")
        assertFailsWith<IllegalArgumentException> { ricalOf(pki).pathForChain(emptyList()) }
    }

    @Test
    fun `rejects chain that does not lead to a RICAL trust anchor`() = runTest {
        val trusted = pki("trusted")
        val other = pki("other")
        assertFailsWith<IllegalArgumentException> { ricalOf(trusted).pathForChain(listOf(other.leaf)) }
    }

    @Test
    fun `rejects reader certificates that do not form a single path`() = runTest {
        val pki = pki("split")
        val other = pki("split-other")
        assertFailsWith<IllegalArgumentException> {
            ricalOf(pki).pathForChain(listOf(pki.leaf, other.leaf))
        }
    }

    @Test
    fun `selects issuer by key identifier when several certificates share the subject DN`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val oldKey = runtime.generateMdocTestKey("rollover-old", signUsages)
        val newKey = runtime.generateMdocTestKey("rollover-new", signUsages)
        val leafKey = runtime.generateMdocTestKey("rollover-leaf", signUsages)
        val dn = "CN=Rollover Root, O=Walt.id"
        val oldRoot = X509CertificateUtil.createSelfSignedCertificate(oldKey, sigAlg) { subjectDn = dn }
        val newRoot = X509CertificateUtil.createSelfSignedCertificate(newKey, sigAlg) { subjectDn = dn }
        val leaf = X509CertificateUtil.createCertificate(newKey, newRoot, sigAlg) {
            subjectDn = "CN=Rollover Reader, O=Walt.id"
            subjectPublicKey(leafKey)
        }
        // old root listed first so a plain subject-DN lookup would pick the wrong one
        val rical = rical(info(oldRoot, 1, null, true), info(newRoot, 2, null, true))
        val (path, anchor) = rical.pathForChain(listOf(leaf))
        assertEquals(listOf(leaf), path)
        assertEquals(newRoot, anchor)
    }
}

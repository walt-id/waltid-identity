package id.walt.credentials.keyresolver.resolvers

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class X5cTrustAnchorsTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun leafUnderConfiguredRootIsTrusted() = runTest {
        val root = selfSigned("CN=Root")
        val leaf = issued(root, "CN=Issuer")

        assertTrue(X5cTrustAnchors.validate(listOf(der(leaf.certificate)), listOf(root.certificate.encodedPem)).isSuccess)
        assertTrue(
            X5cTrustAnchors.validate(
                listOf(der(leaf.certificate), der(root.certificate)),
                listOf(der(root.certificate)),
            ).isSuccess
        )
    }

    @Test
    fun pinnedLeafIsTrusted() = runTest {
        val leaf = issued(selfSigned("CN=Unconfigured root"), "CN=Pinned issuer")

        X5cTrustAnchors.validate(listOf(der(leaf.certificate)), listOf(der(leaf.certificate))).getOrThrow()
    }

    @Test
    fun pinnedLeafPresentedWithItsIssuerIsTrusted() = runTest {
        val root = selfSigned("CN=Unconfigured root")
        val leaf = issued(root, "CN=Pinned issuer")

        X5cTrustAnchors.validate(
            listOf(der(leaf.certificate), der(root.certificate)),
            listOf(der(leaf.certificate)),
        ).getOrThrow()
    }

    @Test
    fun leafUnderPinnedIntermediateIsTrusted() = runTest {
        val root = selfSigned("CN=Unconfigured root")
        val intermediate = issued(root, "CN=Pinned intermediate", ca = true)
        val leaf = issued(intermediate, "CN=Issuer")

        X5cTrustAnchors.validate(
            listOf(der(leaf.certificate), der(intermediate.certificate), der(root.certificate)),
            listOf(der(intermediate.certificate)),
        ).getOrThrow()
    }

    @Test
    fun pinnedSelfSignedEndEntityIsTrusted() = runTest {
        val key = generateKey("CN=Self-signed issuer")
        val certificate = X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "CN=Self-signed issuer"
            extensionBasicConstraints { cA = false }
        }

        X5cTrustAnchors.validate(listOf(der(certificate)), listOf(der(certificate))).getOrThrow()
    }

    @Test
    fun pinnedCaSigningCertificateIsRejected() = runTest {
        val ca = selfSigned("CN=Self-signed CA")

        val failure = X5cTrustAnchors.validate(listOf(der(ca.certificate)), listOf(der(ca.certificate))).exceptionOrNull()

        assertContains(requireNotNull(failure).message.orEmpty(), "cA flag")
    }

    @Test
    fun leafUnderAnotherRootIsRejected() = runTest {
        val trusted = selfSigned("CN=Root")
        val other = selfSigned("CN=Other root")
        val leaf = issued(other, "CN=Issuer")

        assertTrue(X5cTrustAnchors.validate(listOf(der(leaf.certificate)), listOf(der(trusted.certificate))).isFailure)
    }

    @Test
    fun selfSignedLeafIsRejected() = runTest {
        val trusted = selfSigned("CN=Root")
        val leaf = selfSigned("CN=Issuer")

        assertTrue(X5cTrustAnchors.validate(listOf(der(leaf.certificate)), listOf(der(trusted.certificate))).isFailure)
    }

    @Test
    fun rootCarriedOnlyInChainIsNotAnAnchor() = runTest {
        val trusted = selfSigned("CN=Root")
        val attackerRoot = selfSigned("CN=Root")
        val leaf = issued(attackerRoot, "CN=Issuer")

        assertTrue(
            X5cTrustAnchors.validate(
                listOf(der(leaf.certificate), der(attackerRoot.certificate)),
                listOf(der(trusted.certificate)),
            ).isFailure
        )
    }

    @Test
    fun expiredLeafIsRejected() = runTest {
        val root = selfSigned("CN=Root")
        val now = Clock.System.now()
        val leaf = issued(root, "CN=Issuer", X509Certificate.Validity(now - 730.days, now - 365.days))

        assertTrue(X5cTrustAnchors.validate(listOf(der(leaf.certificate)), listOf(der(root.certificate))).isFailure)
    }

    @Test
    fun emptyTrustRootsAreRejected() = runTest {
        val leaf = selfSigned("CN=Issuer")

        assertTrue(X5cTrustAnchors.validate(listOf(der(leaf.certificate)), emptyList()).isFailure)
    }

    @Test
    fun unparsableTrustRootIsRejected() {
        assertFailsWith<Exception> { X5cTrustAnchors.requireParsable(listOf("not a certificate")) }
    }

    private class Issued(val key: Key, val certificate: X509Certificate)

    private fun der(certificate: X509Certificate) = Base64.encode(certificate.encodedDer.toByteArray())

    private suspend fun generateKey(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private suspend fun selfSigned(dn: String): Issued {
        val key = generateKey(dn)
        return Issued(key, X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) { subjectDn = dn })
    }

    private suspend fun issued(
        issuer: Issued,
        dn: String,
        validity: X509Certificate.Validity? = null,
        ca: Boolean = false,
    ): Issued {
        val key = generateKey(dn)
        val certificate = X509CertificateUtil.createCertificate(issuer.key, issuer.certificate, sigAlg) {
            subjectDn = dn
            subjectPublicKey(key)
            validity?.let { this.validity = it }
            if (ca) extensionBasicConstraints { cA = true }
        }
        return Issued(key, certificate)
    }
}

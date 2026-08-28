package id.walt.x509

import id.walt.certificate.x509.TestKeyUtil
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class OrderedCertificateChainPlatformTest {
    @Test
    fun `platform certificate facade validates an ISO document signer chain`() = runTest {
        val rootKey = TestKeyUtil.genEcKey("ordered-chain-platform-root")
        val signerKey = TestKeyUtil.genEcKey("ordered-chain-platform-signer")
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val root = X509CertificateUtil.createSelfSignedCertificate(rootKey, algorithm) {
            profileIaCaRootCertificate(
                issuerDnCountryCode = "AT",
                issuerDnCommonName = "Platform test IACA",
                issuerUri = "https://issuer.example",
            )
        }
        val signer = X509CertificateUtil.createCertificate(rootKey, root, algorithm) {
            profileDocumentSignerCertificate(
                subjectKey = signerKey,
                subjectDnCountryCode = "AT",
                subjectDnCommonName = "Platform test document signer",
                issuerUri = "https://issuer.example",
                crlDistributionPointUri = "https://issuer.example/crl",
            )
        }
        val rootDer = CertificateDer(root.encodedDer.toByteArray())
        val signerDer = CertificateDer(signer.encodedDer.toByteArray())

        verifyOrderedCertificateChainSignatures(listOf(signerDer, rootDer))
        signerDer.validateDocumentSigningCertificateUsage()
        rootDer.validateCertificateAuthorityUsage()
        assertContentEquals(rootDer.subjectKeyIdentifier, signerDer.authorityKeyIdentifier)
        assertFailsWith<IllegalArgumentException> {
            rootDer.validateDocumentSigningCertificateUsage()
        }
    }
}

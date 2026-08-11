package id.walt.certificate.x509

import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmX509CertificateSigningTest {

    @Test
    fun shouldSignCertificateWithRsaKey() = runTest {
        val issuerKey = TestKeyUtil.genRsaKey("rsa-key")
        val sigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
        signAndValidateCertificate(issuerKey, sigAlg)
    }

    @Test
    fun shouldSignCertificateWithEcKey() = runTest {
        val issuerKey = TestKeyUtil.genEcKey("ec-key", EcCurve.P256)
        val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        signAndValidateCertificate(issuerKey, sigAlg)
    }

    @Test
    fun shouldSignCertificateWithEdKey() = runTest {
        val issuerKey = TestKeyUtil.genEdKey("ed-key", EdwardsCurve.ED25519)
        val sigAlg = SignatureAlgorithm.EdDsa
        signAndValidateCertificate(issuerKey, sigAlg)
    }

    suspend fun signAndValidateCertificate(issuerKey: Key, sigAlg: SignatureAlgorithm) {
        val rootCert = assertNotNull(X509CertificateUtil.createSelfSignedCertificate(issuerKey, sigAlg) {
            subjectDn = "CN=Test, OU=Walt.id, O=Walt.id, L=Graz, C=AT"
        })
            .also { cert ->
                cert.data.subjectPublicKeyInfo.restore(TestKeyUtil.runtime).also {
                    assertEquals(issuerKey.spec, it.spec)
                }
                val result =
                    rootUtil.validateCertificateChain(listOf(cert), InMemoryTrustStore(listOf(cert)))
                assertTrue(result.valid)
            }

        assertNotNull(X509CertificateUtil.createCertificate(issuerKey, rootCert, sigAlg) {
            subjectDn = "CN=Test Leaf, OU=Walt.id, O=Walt.id, L=Graz, C=AT"
            subjectPublicKey(issuerKey)
        }).also { cert ->
            cert.data.subjectPublicKeyInfo.restore(TestKeyUtil.runtime).also {
                assertEquals(issuerKey.spec, it.spec)
            }
            val result = X509CertificateUtil.validateCertificateChain(
                listOf(cert),
                InMemoryTrustStore(listOf(rootCert))
            )
            assertTrue(result.valid)
        }
    }

    companion object {
        val rootUtil = X509CertificateUtil {
            addValidators(X509CertificateBasicConstraintsValidator(leafCanBeCa = true))
        }
    }
}

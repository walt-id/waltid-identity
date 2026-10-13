package id.walt.certificate.x509

import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsIsCaValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509CertificateBasicConstraintsIsCaValidatorTest {

    private val sigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
    private val util = X509CertificateUtil { addValidators(X509CertificateBasicConstraintsIsCaValidator()) }

    // Default validators (e.g. basicConstraints, which rejects a CA leaf) stay active, so only the log entries
    // of the validator under test are evaluated.
    private suspend fun validate(cert: X509Certificate) =
        util.validateCertificateChain(listOf(cert), InMemoryTrustStore(listOf(cert))).let { result ->
            ValidationResult(
                result.log.none { it.severity == ValidationResult.Severity.ERROR && it.validatorId == X509CertificateBasicConstraintsIsCaValidator.ID },
                result.log.filter { it.validatorId == X509CertificateBasicConstraintsIsCaValidator.ID }
            )
        }

    @Test
    fun shouldAcceptCaCertificateEvenAsLeaf() = runTest {
        val cert = util.createSelfSignedCertificate(TestKeyUtil.genRsaKey("root"), sigAlg) {
            subjectDn = "cn=Root CA, o=Walt.id"
            extensionBasicConstraints { cA = true }
        }
        assertTrue(validate(cert).valid)
    }

    @Test
    fun shouldRejectNonCaCertificate() = runTest {
        val cert = util.createSelfSignedCertificate(TestKeyUtil.genRsaKey("leaf"), sigAlg) {
            subjectDn = "cn=Leaf, o=Walt.id"
            extensionBasicConstraints { cA = false }
        }
        val result = validate(cert)
        assertFalse(result.valid)
        assertTrue(result.log.any {
            it.severity == ValidationResult.Severity.ERROR &&
                    it.validatorId == X509CertificateBasicConstraintsIsCaValidator.ID &&
                    it.message.contains("must have cA flag set")
        })
    }

    @Test
    fun shouldRejectCertificateWithoutBasicConstraints() = runTest {
        // self-signed certificates get a default basic constraints extension, so use a signed one
        val caKey = TestKeyUtil.genRsaKey("root")
        val caCert = util.createSelfSignedCertificate(caKey, sigAlg) {
            subjectDn = "cn=Root CA, o=Walt.id"
            extensionBasicConstraints { cA = true }
        }
        val cert = util.createCertificate(caKey, caCert, sigAlg) {
            subjectDn = "cn=Plain, o=Walt.id"
            subjectPublicKey(TestKeyUtil.genRsaKey("plain"))
        }
        val result = validate(cert)
        assertFalse(result.valid)
        assertTrue(result.log.any {
            it.severity == ValidationResult.Severity.ERROR &&
                    it.validatorId == X509CertificateBasicConstraintsIsCaValidator.ID &&
                    it.message.contains("is not present")
        })
    }

    @Test
    fun shouldWarnOnlyWhenNotCritical() = runTest {
        val cert = util.createSelfSignedCertificate(TestKeyUtil.genRsaKey("root"), sigAlg) {
            subjectDn = "cn=Root CA, o=Walt.id"
            extensionBasicConstraints {
                critical = false
                cA = true
            }
        }
        val result = validate(cert)
        assertTrue(result.valid)
        assertTrue(result.log.any {
            it.severity == ValidationResult.Severity.WARNING &&
                    it.validatorId == X509CertificateBasicConstraintsIsCaValidator.ID &&
                    it.message.contains("must have critical flag set")
        })
    }
}

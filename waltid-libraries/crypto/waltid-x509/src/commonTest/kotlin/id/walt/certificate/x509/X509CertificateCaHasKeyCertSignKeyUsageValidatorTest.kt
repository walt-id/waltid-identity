package id.walt.certificate.x509

import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension.KeyUsage
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateCaHasKeyCertSignKeyUsageValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509CertificateCaHasKeyCertSignKeyUsageValidatorTest {

    private val sigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
    private val util = X509CertificateUtil { addValidators(X509CertificateCaHasKeyCertSignKeyUsageValidator()) }
    private val validatorId = X509CertificateCaHasKeyCertSignKeyUsageValidator.ID

    /**
     * Builds a chain of root CA (configured by [rootKeyUsage]) and leaf, and returns only the findings of the
     * validator under test. The validator only inspects non-leaf certificates, so a chain of two is required.
     */
    private suspend fun validate(rootKeyUsage: (id.walt.certificate.x509.extension.KeyUsageExtension.Builder.() -> Unit)?): ValidationResult {
        val caKey = TestKeyUtil.genRsaKey("root")
        val caCert = util.createSelfSignedCertificate(caKey, sigAlg) {
            subjectDn = "cn=Root CA, o=Walt.id"
            extensionBasicConstraints { cA = true }
            if (rootKeyUsage != null) extensionKeyUsage(rootKeyUsage)
        }
        val leaf = util.createCertificate(caKey, caCert, sigAlg) {
            subjectDn = "cn=Leaf, o=Walt.id"
            subjectPublicKey(TestKeyUtil.genRsaKey("leaf"))
        }
        val result = util.validateCertificateChain(listOf(caCert, leaf), InMemoryTrustStore(listOf(caCert)))
        val entries = result.log.filter { it.validatorId == validatorId }
        return ValidationResult(entries.none { it.severity == ValidationResult.Severity.ERROR }, entries)
    }

    @Test
    fun shouldAcceptCaWithKeyCertSign() = runTest {
        val result = validate {
            critical = true
            addKeyUsage(KeyUsage.keyCertSign, KeyUsage.cRLSign)
        }
        assertTrue(result.valid)
        assertTrue(result.log.none { it.severity != ValidationResult.Severity.INFO }, result.log.toString())
    }

    @Test
    fun shouldAcceptCaWithoutKeyUsageExtension() = runTest {
        val result = validate(null)
        assertTrue(result.valid)
        assertTrue(result.log.none { it.severity != ValidationResult.Severity.INFO }, result.log.toString())
    }

    @Test
    fun shouldRejectCaWithoutKeyCertSign() = runTest {
        val result = validate {
            critical = true
            addKeyUsage(KeyUsage.digitalSignature)
        }
        assertFalse(result.valid)
        assertTrue(result.log.any {
            it.severity == ValidationResult.Severity.ERROR && it.message.contains("keyCertSign")
        })
    }

    @Test
    fun shouldWarnOnlyWhenNotCritical() = runTest {
        val result = validate {
            critical = false
            addKeyUsage(KeyUsage.keyCertSign)
        }
        assertTrue(result.valid)
        assertTrue(result.log.any {
            it.severity == ValidationResult.Severity.WARNING && it.message.contains("critical flag")
        })
    }
}

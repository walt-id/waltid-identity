package id.walt.certificate.x509

import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509CertificateBasicConstraintsValidationTest {

    @Test
    fun validationShouldFailBecausePathLengthConstraintIsViolated() = runTest {
        val sigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
        val caKey = TestKeyUtil.genRsaKey("root")
        val caCert = caTool.createSelfSignedCertificate(caKey, sigAlg) {
            subjectDn = "cn=Root CA, o=Walt.id"
            extensionBasicConstraints {
                cA = true
                pathLenConstraint = 1
            }
        }
        val i1Key = TestKeyUtil.genRsaKey("i1")
        val i1Cert = caTool.createCertificate(caKey, caCert, sigAlg) {
            subjectDn = "cn=Intermediate 1 CA, o=Walt.id"
            subjectPublicKey(i1Key)
            extensionBasicConstraints {
                cA = true
            }
        }
        val i2Key = TestKeyUtil.genRsaKey("i2")
        val i2Cert = caTool.createCertificate(i1Key, i1Cert, sigAlg) {
            subjectDn = "cn=Intermediate 2 CA, o=Walt.id"
            subjectPublicKey(i2Key)
            extensionBasicConstraints {
                cA = true
            }
        }

        val leafKey = TestKeyUtil.genRsaKey("leaf")
        val leafCert = caTool.createCertificate(i2Key, i2Cert, sigAlg) {
            subjectDn = "cn=Leaf, o=Walt.id"
            subjectPublicKey(leafKey)
        }
        X509CertificateUtil.validateCertificateChain(
            listOf(leafCert, i2Cert, i1Cert),
            InMemoryTrustStore(listOf(caCert))
        ).also {
            assertFalse(it.valid)
            assertTrue(it.log.any {
                it.severity == ValidationResult.Severity.ERROR &&
                        it.validatorId == X509CertificateBasicConstraintsValidator.ID &&
                        it.message.contains("path length constraint")
            }, "Expected path length constraint violation not in log")
        }
    }

    @Test
    fun validationShouldSucceedBecausePathLengthConstraintIsMeet() = runTest {
        val sigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
        val caKey = TestKeyUtil.genRsaKey("root")
        val caCert = caTool.createSelfSignedCertificate(caKey, sigAlg) {
            subjectDn = "cn=Root CA, o=Walt.id"
            extensionBasicConstraints {
                cA = true
                pathLenConstraint = 1
            }
        }
        SignatureValidationUtil.verifyPemChain(caCert.encodedPem, caCert.encodedPem)
        val i1Key = TestKeyUtil.genRsaKey("i1")
        val i1Cert = caTool.createCertificate(caKey, caCert, sigAlg) {
            subjectDn = "cn=Intermediate 1 CA, o=Walt.id"
            subjectPublicKey(i1Key)
            extensionBasicConstraints {
                cA = true
            }
        }
        caTool.validateCertificateChain(listOf(i1Cert), InMemoryTrustStore(listOf(caCert))).also {
            assertTrue(it.valid)
        }

        val leafKey = TestKeyUtil.genRsaKey("leaf")
        val leafCert = X509CertificateUtil.createCertificate(i1Key, i1Cert, sigAlg) {
            subjectDn = "cn=Leaf, o=Walt.id"
            subjectPublicKey(leafKey)
        }
        X509CertificateUtil.validateCertificateChain(listOf(leafCert, i1Cert), InMemoryTrustStore(listOf(caCert)))
            .also {
                assertTrue(it.valid)
            }
    }

    @Test
    fun validationShouldSucceedAlthoughBasicConstraintCriticalFlagIsDisabled() = runTest {
        val sigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
        val caKey = TestKeyUtil.genRsaKey("root")
        val caCert = caTool.createSelfSignedCertificate(caKey, sigAlg) {
            subjectDn = "cn=Root CA, o=Walt.id"
            extensionBasicConstraints {
                critical = false
                cA = true
            }
        }
        caTool.validateCertificateChain(listOf(caCert), InMemoryTrustStore(listOf(caCert))).also {
            assertTrue(it.valid)
            assertTrue(it.log.any { it.severity == ValidationResult.Severity.WARNING &&
            it.validatorId == X509CertificateBasicConstraintsValidator.ID &&
                    it.message.contains("must have critical flag set")})
        }
    }

    companion object {
        val caTool = X509CertificateUtil { addValidators(X509CertificateBasicConstraintsValidator(leafCanBeCa = true)) }
    }
}
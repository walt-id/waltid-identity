package id.walt.certificate.x509

import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.testdata.TestDataCertificates.googleComCrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsRootR4CrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsWe2CrtPem
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.TypedKeyGenerationRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class X509CertificateChainValidationTest {

    @Test
    fun shouldFindMissingRootCertificateErrorChain() = runTest {
        val certificatePem = googleComCrtPem
        assertNotNull(certificatePem)
        val result = certUtil.validatePemCertificateChain(certificatePem)
        assertFalse(result.valid)
        result.log.filter {
            it.validatorId == X509CertificateSignatureValidator.ID
                    && it.severity == ValidationResult.Severity.ERROR
        }
            .also { signatureValidatorLog ->
                assertEquals(1, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.ERROR, signatureValidatorLog[0].severity)
                assertEquals("CN=*.google.com", signatureValidatorLog[0].subjectDn)
                assertEquals(
                    "Trusted issuer certificate 'C=US,O=Google Trust Services,CN=WE2' not found",
                    signatureValidatorLog[0].message
                )
            }
    }


    @Test
    fun shouldValidateGoogleCertificateChainWithOneEntry() = runTest {
        val result = certUtil.validatePemCertificateChain(gtsWe2CrtPem)
        assertTrue(result.valid, "Validation log: ${result.log}")
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
            .also { signatureValidatorLog ->
                assertEquals(2, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[0].severity)
                assertEquals("C=US,O=Google Trust Services,CN=WE2", signatureValidatorLog[0].subjectDn)
            }
    }

    @Test
    fun shouldValidateGoogleCertificateChainWithTwoEntries() = runTest {
        val certificatePem = listOf(
            googleComCrtPem,
            gtsWe2CrtPem,
            googleComCrtPem,
            gtsWe2CrtPem,
        ).joinToString("\n")
        val result = certUtil.validatePemCertificateChain(certificatePem)
        assertTrue(result.valid)
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
            .also { signatureValidatorLog ->
                assertEquals(4, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[0].severity)
                assertEquals("C=US,O=Google Trust Services,CN=WE2", signatureValidatorLog[0].subjectDn)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[2].severity)
                assertEquals("CN=*.google.com", signatureValidatorLog[2].subjectDn)
            }
    }

    @Test
    fun shouldValidateCertChainWithTrustAnchorInTheMiddle() = runTest {
        val rootCaKey = KeyManager.createKey(TypedKeyGenerationRequest.Jwk())
        val rootCaCert = X509CertificateUtil.createSelfSignedCertificate(rootCaKey) {
            subjectDn = "CN=Root CA, OU=Walt.id"
            extensionBasicConstraints {
                cA = true
            }
        }
        val intermediateCaKey = KeyManager.createKey(TypedKeyGenerationRequest.Jwk())
        val intermediateCaCert = X509CertificateUtil.createCertificate(rootCaKey, rootCaCert) {
            subjectDn = "CN=Intermediate CA, OU=Walt.id"
            subjectPublicKey(intermediateCaKey)
            extensionBasicConstraints {
                cA = true
            }
        }

        val leafKey = KeyManager.createKey(TypedKeyGenerationRequest.Jwk())
        val leafCert = X509CertificateUtil.createCertificate(intermediateCaKey, intermediateCaCert) {
            subjectDn = "CN=Leaf, OU=Walt.id"
            subjectPublicKey(leafKey)
        }
        val trust = InMemoryTrustStore(listOf(rootCaCert, intermediateCaCert))
        certUtil.validateCertificateChain(listOf(leafCert), trust).also {
            assertTrue(it.valid)
        }
        certUtil.validateCertificateChain(listOf(intermediateCaCert, leafCert), trust).also {
            assertTrue(it.valid)
        }
        certUtil.validateCertificateChain(listOf(intermediateCaCert, leafCert, rootCaCert), trust).also {
            assertTrue(it.valid)
        }
    }

    companion object {

        val trustStore = InMemoryTrustStore(
            listOf(gtsRootR4CrtPem)
                .map { X509CertificateUtil.parseCertificatePem(it) })

        val certUtil = X509CertificateUtil {
            /**
             * Trust store with Google Trust Services root certificate
             * and without a system trust store to ensure the same behavior in JS and JVM
             */
            setTrust(trustStore)
        }
    }
}

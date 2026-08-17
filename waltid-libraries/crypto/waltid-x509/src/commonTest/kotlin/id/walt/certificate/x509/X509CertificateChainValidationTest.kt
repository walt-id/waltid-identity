package id.walt.certificate.x509

import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.testdata.TestDataCertificates.googleComCrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsRootR4CrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsWe2CrtPem
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.crypto.keys.KeyType
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
        val result = caCertUtil.validatePemCertificateChain(gtsWe2CrtPem)
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
        withCertificateTestKey(KeyType.secp256r1) { rootCaKey ->
            val rootCaCert = X509CertificateUtil.createSelfSignedCertificate(rootCaKey) {
                subjectDn = "CN=Root CA, OU=Walt.id"
                extensionBasicConstraints {
                    cA = true
                }
            }

            withCertificateTestKey(KeyType.secp256r1) { intermediateCaKey ->
                val intermediateCaCert = X509CertificateUtil.createCertificate(rootCaKey, rootCaCert) {
                    subjectDn = "CN=Intermediate CA, OU=Walt.id"
                    subjectPublicKey(intermediateCaKey)
                    extensionBasicConstraints {
                        cA = true
                    }
                }

                withCertificateTestKey(KeyType.secp256r1) { leafKey ->
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
            }
        }
    }

    @Test
    fun trustOverrideShouldReplaceBaseTrustStoreNotMergeWithIt() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { baseRootKey ->
            val baseRootCert = X509CertificateUtil.createSelfSignedCertificate(baseRootKey) {
                subjectDn = "CN=Base Trusted Root, OU=Walt.id"
                extensionBasicConstraints {
                    cA = true
                }
            }

            withCertificateTestKey(KeyType.secp256r1) { leafKey ->
                val leafCert = X509CertificateUtil.createCertificate(baseRootKey, baseRootCert) {
                    subjectDn = "CN=Leaf, OU=Walt.id"
                    subjectPublicKey(leafKey)
                }

                // A util whose configured (base) trust store trusts baseRootCert - simulating e.g. a
                // platform/system trust store configured once for the util.
                val utilWithBaseTrust = X509CertificateUtil {
                    setTrust(InMemoryTrustStore(listOf(baseRootCert)))
                }

                // Sanity check: without a trustOverride, the base trust store is used and the chain validates.
                assertTrue(utilWithBaseTrust.validateCertificateChain(listOf(leafCert)).valid)

                withCertificateTestKey(KeyType.secp256r1) { unrelatedRootKey ->
                    // An unrelated root, with no relation to baseRootCert/leafCert.
                    val unrelatedRootCert = X509CertificateUtil.createSelfSignedCertificate(unrelatedRootKey) {
                        subjectDn = "CN=Unrelated Root, OU=Walt.id"
                        extensionBasicConstraints {
                            cA = true
                        }
                    }

                    // Passing a trustOverride must use ONLY that override, not merge it with the base trust
                    // store - otherwise a chain that is only trusted via the base store would incorrectly
                    // validate here too, silently reintroducing whatever trust the base store carries (e.g.
                    // the platform's system CA store) into a call meant to be scoped to the given anchors.
                    val result = utilWithBaseTrust.validateCertificateChain(listOf(leafCert), InMemoryTrustStore(listOf(unrelatedRootCert)))
                    assertFalse(result.valid, "trustOverride must replace the base trust store, not merge with it: ${result.log}")
                }
            }
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

        val caCertUtil = X509CertificateUtil {
            /**
             * Trust store with Google Trust Services root certificate
             * and without a system trust store to ensure the same behavior in JS and JVM
             */
            setTrust(trustStore)
            addValidators(X509CertificateBasicConstraintsValidator(leafCanBeCa = true))
        }
    }
}

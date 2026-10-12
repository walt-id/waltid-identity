package id.walt.certificate.x509

import id.walt.certificate.x509.TestKeyUtil.genEcKey
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.testdata.TestDataCertificates.googleComCrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsRootR4CrtPem
import id.walt.certificate.x509.testdata.TestDataCertificates.gtsWe2CrtPem
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateAuthorityKeyIdValidator
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

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
                assertTrue(
                    signatureValidatorLog[0].message.contains("Trusted issuer certificate 'C=US,O=Google Trust Services,CN=WE2'") &&
                            signatureValidatorLog[0].message.contains("not found")
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
                assertEquals(
                    "C=US,O=Google Trust Services,CN=WE2",
                    signatureValidatorLog[0].subjectDn
                )
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
        val result = capturedChainCertUtil.validatePemCertificateChain(certificatePem)
        assertTrue(result.valid)
        result.log.filter { it.validatorId == X509CertificateSignatureValidator.ID }
            .also { signatureValidatorLog ->
                assertEquals(4, signatureValidatorLog.size)
                assertEquals(ValidationResult.Severity.INFO, signatureValidatorLog[0].severity)
                assertEquals(
                    "C=US,O=Google Trust Services,CN=WE2",
                    signatureValidatorLog[0].subjectDn
                )
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
                val intermediateCaCert =
                    X509CertificateUtil.createCertificate(rootCaKey, rootCaCert) {
                        subjectDn = "CN=Intermediate CA, OU=Walt.id"
                        subjectPublicKey(intermediateCaKey)
                        extensionBasicConstraints {
                            cA = true
                        }
                    }

                withCertificateTestKey(KeyType.secp256r1) { leafKey ->
                    val leafCert = X509CertificateUtil.createCertificate(
                        intermediateCaKey,
                        intermediateCaCert
                    ) {
                        subjectDn = "CN=Leaf, OU=Walt.id"
                        subjectPublicKey(leafKey)
                    }
                    val trust = InMemoryTrustStore(listOf(rootCaCert, intermediateCaCert))
                    certUtil.validateCertificateChain(listOf(leafCert), trust).also {
                        assertTrue(it.valid)
                    }
                    certUtil.validateCertificateChain(listOf(intermediateCaCert, leafCert), trust)
                        .also {
                            assertTrue(it.valid)
                        }
                    certUtil.validateCertificateChain(
                        listOf(
                            intermediateCaCert,
                            leafCert,
                            rootCaCert
                        ), trust
                    ).also {
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
                    val unrelatedRootCert =
                        X509CertificateUtil.createSelfSignedCertificate(unrelatedRootKey) {
                            subjectDn = "CN=Unrelated Root, OU=Walt.id"
                            extensionBasicConstraints {
                                cA = true
                            }
                        }

                    // Passing a trustOverride must use ONLY that override, not merge it with the base trust
                    // store - otherwise a chain that is only trusted via the base store would incorrectly
                    // validate here too, silently reintroducing whatever trust the base store carries (e.g.
                    // the platform's system CA store) into a call meant to be scoped to the given anchors.
                    val result = utilWithBaseTrust.validateCertificateChain(
                        listOf(leafCert),
                        InMemoryTrustStore(listOf(unrelatedRootCert))
                    )
                    assertFalse(
                        result.valid,
                        "trustOverride must replace the base trust store, not merge with it: ${result.log}"
                    )
                }
            }
        }
    }

    /**
     * [X509CertificateUtil.createCertificate] always sets the AKI from the issuer key, so a mismatch can only
     * be produced by validating against a trusted certificate that has the leaf's issuer DN but another key.
     */
    @Test
    fun authorityKeyIdValidatorShouldAcceptMatchingIssuerSubjectKeyId() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { rootCaKey ->
            val rootCaCert = createRootCa(rootCaKey)
            withCertificateTestKey(KeyType.secp256r1) { leafKey ->
                val leafCert = createLeaf(rootCaKey, rootCaCert, leafKey)

                val result = certUtil.validateCertificateChain(
                    listOf(leafCert),
                    InMemoryTrustStore(listOf(rootCaCert))
                )

                assertTrue(result.valid, "Validation log: ${result.log}")
                assertEquals(emptyList(), result.authorityKeyIdLog(ValidationResult.Severity.ERROR))
            }
        }
    }

    @Test
    fun authorityKeyIdValidatorShouldReportIssuerSubjectKeyIdMismatch() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { rootCaKey ->
            val rootCaCert = createRootCa(rootCaKey)
            withCertificateTestKey(KeyType.secp256r1) { leafKey ->
                val leafCert = createLeaf(rootCaKey, rootCaCert, leafKey)

                withCertificateTestKey(KeyType.secp256r1) { otherRootCaKey ->
                    // Same subject DN as the real issuer, but a different key and therefore a different SKI.
                    val otherRootCaCert = createRootCa(otherRootCaKey)
                    assertEquals(rootCaCert.data.subjectDn, otherRootCaCert.data.subjectDn)

                    val result = certUtil.validateCertificateChain(
                        listOf(leafCert),
                        InMemoryTrustStore(listOf(otherRootCaCert))
                    )

                    assertFalse(result.valid, "Validation log: ${result.log}")
                    assertTrue(result.errorLog.any {
                        it.message.contains("Trusted issuer certificate 'CN=Root CA,OU=Walt.id'") &&
                                it.message.contains("not found")
                    })
                }
            }
        }
    }

    @Test
    fun authorityKeyIdValidatorShouldNotReportAnythingWhenIssuerIsNotKnown() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { rootCaKey ->
            val rootCaCert = createRootCa(rootCaKey)
            withCertificateTestKey(KeyType.secp256r1) { leafKey ->
                val leafCert = createLeaf(rootCaKey, rootCaCert, leafKey)

                val result =
                    certUtil.validateCertificateChain(listOf(leafCert), InMemoryTrustStore())

                // The missing issuer is a trust problem reported by the signature validator, not by this one.
                assertFalse(result.valid, "Validation log: ${result.log}")
                assertEquals(
                    emptyList(),
                    result.log.filter {
                        it.validatorId == X509CertificateAuthorityKeyIdValidator.ID
                                && it.severity != ValidationResult.Severity.INFO
                    }
                )
            }
        }
    }

    @Test
    fun shouldRejectIncoherentCertificateChain() = runTest {
        val rootCaKeyA = genEcKey("rootCaA")
        val rootCaCertA = X509CertificateUtil.createSelfSignedCertificate(rootCaKeyA, ecdsaSigAlg) {
            subjectDn = "cn=Root"
        }

        val leafKeyA = genEcKey("leafA")
        val leafCertA =
            X509CertificateUtil.createCertificate(rootCaKeyA, rootCaCertA, ecdsaSigAlg) {
                subjectDn = "cn=Leaf"
                subjectPublicKey(leafKeyA)
            }


        val rootCaKeyB = genEcKey("rootCaB")
        val rootCaCertB = X509CertificateUtil.createSelfSignedCertificate(rootCaKeyB, ecdsaSigAlg) {
            subjectDn = "cn=Root"
        }

        val leafKeyB = genEcKey("leafB")
        val leafCertB =
            X509CertificateUtil.createCertificate(rootCaKeyB, rootCaCertB, ecdsaSigAlg) {
                subjectDn = "cn=Leaf"
                subjectPublicKey(leafKeyB)
            }

        assertFailsWith<IllegalArgumentException> {
            X509CertificateUtil.validateCertificateChain(
                listOf(leafCertB, leafCertA),
                InMemoryTrustStore(listOf(rootCaCertA))
            )
        }
    }


    companion object {

        private val ecdsaSigAlg: SignatureAlgorithm = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER
        )

        // Google certificates are valid till 24.09.2026
        private val timeOffset = Clock.System.now() - Instant.parse("2026-09-01T00:00:00Z")

        private val testClock: Clock = object : Clock {
            override fun now(): Instant =
                Clock.System.now() - timeOffset
        }

        val trustStore = InMemoryTrustStore(
            listOf(gtsRootR4CrtPem)
                .map { X509CertificateUtil.parseCertificatePem(it) })

        /**
         * A captured real leaf certificate lives about 90 days, so judging it against the system clock means
         * the test validating it starts failing on the day it expires - which is what happened on 14 September
         * 2026. What that test covers is chain and signature validation, not whether a certificate pasted into
         * test data is valid today.
         *
         * So validity is judged at the midpoint of the leaf's own window: inside it by construction, and still
         * inside it if someone later drops in a freshly captured certificate. Replacing the default validator
         * works because [X509CertificateUtilBuilder.addValidators] substitutes by validator id.
         *
         * Deliberately not applied to [certUtil] or [caCertUtil]: those are also used by tests that build
         * their certificates at the real "now", and a clock pinned into the past makes those not yet valid.
         */
        private val googleLeafValidity =
            X509CertificateUtil.parseCertificatePem(googleComCrtPem).data.validity
        private val whileGoogleLeafWasValid = X509CertificateValidityValidator(
            clock = object : Clock {
                override fun now(): Instant =
                    googleLeafValidity.notBefore + (googleLeafValidity.notAfter - googleLeafValidity.notBefore) / 2
            },
        )

        /** [certUtil] with validity judged while the captured leaf was valid. */
        val capturedChainCertUtil = X509CertificateUtil {
            setTrust(trustStore)
            addValidators(whileGoogleLeafWasValid)
        }

        val certUtil = X509CertificateUtil {
            /**
             * Trust store with Google Trust Services root certificate
             * and without a system trust store to ensure the same behavior in JS and JVM
             */
            setTrust(trustStore)
            addValidators(
                X509CertificateValidityValidator(
                    allowValidityInFuture = true,
                    clock = testClock
                )
            )
        }

        val caCertUtil = X509CertificateUtil {
            /**
             * Trust store with Google Trust Services root certificate
             * and without a system trust store to ensure the same behavior in JS and JVM
             */
            setTrust(trustStore)
            addValidators(
                X509CertificateBasicConstraintsValidator(leafCanBeCa = true),
                X509CertificateValidityValidator(clock = testClock)
            )
        }

        private suspend fun createRootCa(rootCaKey: Key) =
            X509CertificateUtil.createSelfSignedCertificate(rootCaKey) {
                subjectDn = "CN=Root CA, OU=Walt.id"
                extensionBasicConstraints {
                    cA = true
                }
            }

        private suspend fun createLeaf(rootCaKey: Key, rootCaCert: X509Certificate, leafKey: Key) =
            X509CertificateUtil.createCertificate(rootCaKey, rootCaCert) {
                subjectDn = "CN=Leaf, OU=Walt.id"
                subjectPublicKey(leafKey)
            }

        private fun ValidationResult.authorityKeyIdLog(severity: ValidationResult.Severity) =
            log.filter { it.validatorId == X509CertificateAuthorityKeyIdValidator.ID && it.severity == severity }

    }
}

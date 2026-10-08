package id.walt.certificate.x509.validation

import id.walt.certificate.x509.TestKeyUtil
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.validator.X509CertificateHasIaCaContactInformationValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509CertificateHasIaCaContactInformationValidatorTest {

    private val sigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
    private val validatorId = X509CertificateHasIaCaContactInformationValidator.ID

    @Test
    fun shouldAcceptEmailContact() = runTest {
        val result =
            createAndValidateCertificate(true) {
                addEmail("iaca@walt.id")
            }
        assertTrue(result.valid)
        assertTrue(
            result.log.none { it.severity != ValidationResult.Severity.INFO },
            result.log.toString()
        )
    }

    @Test
    fun shouldAcceptUriContact() = runTest {
        val result =
            createAndValidateCertificate(true) {
                addUri("https://walt.id/contact")
            }
        assertTrue(result.valid)
        assertTrue(
            result.log.none { it.severity != ValidationResult.Severity.INFO },
            result.log.toString()
        )
    }

    @Test
    fun shouldWarnWhenExtensionMissingAndNotMandatory() = runTest {
        val result = createAndValidateCertificate(false, null)
        assertTrue(result.valid)
        assertEquals(1, result.log.count { it.severity == ValidationResult.Severity.WARNING })
    }

    @Test
    fun shouldWarnByDefaultWhenExtensionMissing() = runTest {
        val result = createAndValidateCertificate(null, null)
        assertTrue(result.valid)
        assertEquals(1, result.log.count { it.severity == ValidationResult.Severity.WARNING })
    }

    @Test
    fun shouldRejectMissingExtensionWhenMandatory() = runTest {
        val result = createAndValidateCertificate(true, null)
        assertFalse(result.valid)
        assertTrue(result.log.any {
            it.severity == ValidationResult.Severity.ERROR && it.message.contains("IssuerAltName")
        })
    }

    @Test
    fun shouldRejectNamesOfOtherTypesWhenMandatory() = runTest {
        val result = createAndValidateCertificate(true) {
            addDnsName("walt.id")
            addIpAddress("127.0.0.1")
        }
        assertFalse(result.valid)
    }

    @Test
    fun shouldWarnForNamesOfOtherTypesWhenNotMandatory() = runTest {
        val result = createAndValidateCertificate(false) {
            addDnsName("walt.id")
        }
        assertTrue(result.valid)
        assertEquals(1, result.log.count { it.severity == ValidationResult.Severity.WARNING })
    }

    /** Validates a self-signed certificate and returns only the findings of the validator under test. */
    private suspend fun createAndValidateCertificate(
        mandatory: Boolean?,
        issuerAltName: (IssuerAlternativeNameExtension.Builder.() -> Unit)?,
    ): ValidationResult {
        val validator = mandatory
            ?.let { X509CertificateHasIaCaContactInformationValidator(it) }
            ?: X509CertificateHasIaCaContactInformationValidator()

        val util = X509CertificateUtil { addValidators(validator) }
        val cert = util.createSelfSignedCertificate(TestKeyUtil.genRsaKey("contact"), sigAlg) {
            subjectDn = "cn=Root CA, o=Walt.id"
            if (issuerAltName != null) extensionIssuerAltName(issuerAltName)
        }
        val result = util.validateCertificateChain(listOf(cert), InMemoryTrustStore(listOf(cert)))
        val entries = result.log.filter { it.validatorId == validatorId }
        return ValidationResult(
            entries.none
            { it.severity == ValidationResult.Severity.ERROR },
            entries
        )
    }
}
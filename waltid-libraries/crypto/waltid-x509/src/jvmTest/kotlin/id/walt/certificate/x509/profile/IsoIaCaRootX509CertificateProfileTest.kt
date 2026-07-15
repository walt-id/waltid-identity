package id.walt.certificate.x509.profile

import id.walt.certificate.TestData.intermediateIssuerPrivateKey
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto.keys.JvmJWKKeyCreator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsoIaCaRootX509CertificateProfileTest {

    @Test
    fun shouldCreateIaCaRootCertificate(): Unit = runTest {
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
            profileIaCaRootCertificate(
                issuerDnCountryCode = "at",
                issuerDnOrganizationName = "Walt ID",
                issuerDnCommonName = "walt.id",
                issuerDnSerialNumber = "1234567",
                issuerEmailAddress = "office@walt.id"
            )
        }
        assertEquals("CN=walt.id+SERIALNUMBER=1234567,O=Walt ID,C=AT", cert.data.subjectDn)
        assertEquals("CN=walt.id+SERIALNUMBER=1234567,O=Walt ID,C=AT", cert.data.issuerDn)
        val validationResult = validator.validate(cert)
        assertTrue(validationResult.valid)
        assertFalse(
            validationResult.hasWarnings,
            "Warnings: ${validationResult.log.filter { it.severity == ValidationResult.Severity.WARNING }}"
        )
        assertFalse(validationResult.hasErrors)
    }

    @Test
    fun shouldFindIllegalIssuerDnCountryCodeInIaCaRootCertificate(): Unit = runTest {
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
            profileIaCaRootCertificate(
                issuerDn = "cn=Walt ID,C=Austria",
                issuerEmailAddress = "office@walt.id",
                issuerUri = "https://walt.id"
            )
        }
        assertEquals("CN=Walt ID,C=Austria", cert.data.subjectDn)
        assertEquals("CN=Walt ID,C=Austria", cert.data.issuerDn)
        val validationResult = validator.validate(cert)

        assertFalse(
            validationResult.hasWarnings,
            "Warnings: ${validationResult.log.filter { it.severity == ValidationResult.Severity.WARNING }}"
        )
        assertTrue(validationResult.hasErrors)
        assertTrue(validationResult.log.any {
            it.severity == ValidationResult.Severity.ERROR
                    && it.validatorId == "iso-iaca-root.issuerDn"
        })
        assertFalse(validationResult.valid)
    }

    companion object {
        val key = runBlocking {
            JvmJWKKeyCreator.importPEM(intermediateIssuerPrivateKey).getOrThrow()
        }

        val validator = X509SingleCertificateValidator(listOf(IsoIaCaRootX509CertificateProfile))
    }
}
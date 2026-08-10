package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.withCertificateTestKey
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class IsoDocumentSignerX509CertificateProfileTest {

    //TODO: Add tests for all supported key types
    //      waltid-crypto only supports a subset of key types and every platform has a different set of supported key types
    @Test
    fun shouldCreateValidDocumentSignerCertificate() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { rootCaKey ->
            val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootCaKey) {
                profileIaCaRootCertificate(
                    issuerEmailAddress = "iaca@example.com",
                    issuerUri = "https://iaca.example.com",
                    issuerDnCountryCode = "AT",
                    issuerDnCommonName = "Example IACA for testing Document Signer Profile",
                )
            }

            withCertificateTestKey(KeyType.secp384r1) { subjectKey ->
                val cert = X509CertificateUtil.createCertificate(rootCaKey, rootCert) {
                    profileDocumentSignerCertificate(
                        crlDistributionPointUri = "https://crl.walt.id/crl.der",
                        issuerEmailAddress = "office@walt.id",
                        subjectKey = subjectKey,
                        subjectDnCountryCode = "AT",
                        subjectDnStateOrProvinceName = "Styria",
                        subjectDnLocalityName = "Graz",
                        subjectDnOrganizationName = "Walt ID",
                        subjectDnCommonName = "My Document Signer Certificate",
                        subjectDnSerialNumber = "1234567"
                    )
                }
                val result = validator.validate(cert)
                assertTrue(result.valid, "Validation log: ${result.log}")
            }
        }
    }

    companion object {
        private val validator = X509SingleCertificateValidator(listOf(IsoDocumentSignerX509CertificateProfile))
    }
}

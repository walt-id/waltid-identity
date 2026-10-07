package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.profile.IsoMdocReaderAuthenticationX509CertificateProfile.profileMdocReaderAuthenticationCertificate
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.certificate.x509.withCertificateTestKey
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class IsoMdocReaderAuthenticationX509CertificateProfileTest {

    //TODO: Add tests for all supported key types
    //      waltid-crypto only supports a subset of key types and every platform has a different set of supported key types
    @Test
    fun shouldCreateValidMdocReaderAuthenticationCertificate() = runTest {
        withCertificateTestKey(KeyType.secp256r1) { rootCaKey ->
            val rootCert = X509CertificateUtil.createSelfSignedCertificate(rootCaKey) {
                profileIaCaRootCertificate(
                    issuerEmailAddress = "iaca@example.com",
                    issuerUri = "https://iaca.example.com",
                    issuerDnCountryCode = "AT",
                    issuerDnCommonName = "Example IACA for testing Reader Authentication Profile",
                )
            }

            withCertificateTestKey(KeyType.secp384r1) { subjectKey ->
                val cert = X509CertificateUtil.createCertificate(rootCaKey, rootCert) {
                    profileMdocReaderAuthenticationCertificate(
                        crlDistributionPointUri = "https://crl.walt.id/reader-crl.der",
                        subjectKey = subjectKey,
                        subjectDnCommonName = "My Reader Authentication Certificate",
                    )
                }
                val result = validator.validate(cert)
                assertTrue(result.valid, "Validation log: ${result.log}")
            }
        }
    }

    companion object {
        private val validator = X509SingleCertificateValidator(listOf(IsoMdocReaderAuthenticationX509CertificateProfile))
    }
}

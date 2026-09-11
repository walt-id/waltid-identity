package id.walt.certificate.x509

import id.walt.certificate.TestData
import id.walt.certificate.x509.revocation.CrlTestFixtures
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import java.security.Security
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColdCertificateSignatureValidationTest {
    @Test
    fun certificateAndCsrValidationDoNotRequireGlobalBouncyCastleRegistration() = runTest {
        val services = X509CertificateUtil.services
        val issuer = X509CertificateUtil.parseCertificateDerEncoded(ByteString(CrlTestFixtures.der("EC256_CA")))
        val certificate = X509CertificateUtil.parseCertificateDerEncoded(ByteString(CrlTestFixtures.der("EC256_LEAF")))
        val csr = X509CertificateUtil.parseCsrPem(TestData.csrPem)
        val previous = Security.getProvider("BC")
        val position = Security.getProviders().indexOf(previous) + 1
        Security.removeProvider("BC")
        try {
            assertTrue(services.signatureValidator.validateCertificateSignature(services.cryptoRuntime, issuer.data.subjectPublicKeyInfo, certificate))
            assertTrue(services.signatureValidator.validateCsrSignature(services.cryptoRuntime, csr))
            assertNull(Security.getProvider("BC"))
        } finally {
            if (previous != null) Security.insertProviderAt(previous, position)
        }
    }
}

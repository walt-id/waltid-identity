package id.walt.certificate.x509

import id.walt.certificate.TestData
import id.walt.certificate.x509.signum.SignumDefaults
import id.walt.x509.id.walt.certificate.x509.JavaX509CertificateSerialNumberGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Java is easier to debug
 */
class SignumImplementationTest {

    @Test
    fun shouldParseCsr() {
        val csr = parseCsrPem(TestData.csrPem)
        assertNotNull(csr)
        assertEquals(
            "C=AT,ST=Vienna,L=Vienna,O=Walt.id,CN=://walt.id",
            csr.requestedCertificate.subjectDn
        )
    }

    @Test
    fun shouldParseCertificate() {
        val cert = parseCertificatePem(TestData.GOOGLE_CERTIFICATE_PEM)
        assertNotNull(cert)
        assertEquals("CN=*.google.com", cert.data.subjectDn)
    }


    companion object {
        val defaults = SignumDefaults(JavaX509CertificateSerialNumberGenerator())

        fun parseCsrPem(pem: String): Pkcs10CertificateSigningRequest =
            defaults.csrParser.parseCertificateSigningRequestPem(pem)

        fun parseCertificatePem(pem: String): X509Certificate =
            defaults.certificateParser.parseCertificatePem(pem)
    }
}
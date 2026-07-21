package id.walt.certificate.x509.signum

import id.walt.certificate.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SignumCertificateParserTest {

    @Test
    fun shouldParserCertificate() {
        val cert = parser.parseCertificatePem(TestData.GOOGLE_CERTIFICATE_PEM)
        assertNotNull(cert)
        assertEquals("CN=*.google.com",cert.data.subjectDn)
    }

    companion object {
        val parser = SignumCertificateParser()
    }
}
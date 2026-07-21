package id.walt.certificate.x509

import id.walt.certificate.x509.CertificateAssertionUtil.assertBuildersSerialNoCompliance
import id.walt.x509.iso.ISO_CERT_SERIAL_NUMBER_REQUIRED_LENGTH
import kotlin.test.Test
import kotlin.test.assertEquals

class CertificateSerialNumberGeneratorTest {

    @Test
    fun generatesPositiveNonZeroTwentyOctetSerialNumber() {

        val serialNumber = X509CertificateUtilDefaults.serialNumberGenerator.next()
        assertEquals(
            expected = ISO_CERT_SERIAL_NUMBER_REQUIRED_LENGTH,
            actual = serialNumber.size,
        )
        assertBuildersSerialNoCompliance(serialNumber)
    }
}
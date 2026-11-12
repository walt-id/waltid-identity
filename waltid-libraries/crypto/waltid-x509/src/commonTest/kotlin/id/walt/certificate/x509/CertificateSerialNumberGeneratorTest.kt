package id.walt.certificate.x509

import id.walt.certificate.x509.CertificateAssertionUtil.assertBuildersSerialNoCompliance
import kotlin.test.Test

class CertificateSerialNumberGeneratorTest {

    @Test
    fun generatesPositiveNonZeroSerialNumberWithinLengthBounds() {

        val serialNumber = X509CertificateUtil.nextSerialNumber()
        // Canonical integer encoding may be shorter than the 20-octet maximum.
        assertBuildersSerialNoCompliance(serialNumber)
    }
}

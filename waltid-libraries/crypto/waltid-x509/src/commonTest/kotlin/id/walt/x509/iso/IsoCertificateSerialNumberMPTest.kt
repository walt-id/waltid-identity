package id.walt.x509.iso

import kotlin.test.Test
import kotlin.test.assertEquals

class IsoCertificateSerialNumberMPTest {

    @Test
    fun generatesPositiveNonZeroTwentyOctetSerialNumber() {
        val serialNumber = generateIsoCompliantX509CertificateSerialNo()

        assertEquals(
            expected = ISO_CERT_SERIAL_NUMBER_REQUIRED_LENGTH,
            actual = serialNumber.size,
        )
        assertBuildersSerialNoCompliance(serialNumber)
    }
}

package id.walt.certificate.x509

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertTrue

class CommonX509CertificateSerialNumberGeneratorTest {

    @Test
    fun shouldNotGenerateNegativeSerialNumbers() {
        for (i in 0..100) {
            val serialNumberBytes = generator.next()
            val serialInt = BigInteger(serialNumberBytes.toByteArray())
            assertTrue(serialInt.signum() > 0)
        }
    }

    companion object {
        val generator = CommonX509CertificateSerialNumberGenerator()
    }
}
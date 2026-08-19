package id.walt.x509.id.walt.certificate.x509

import id.walt.certificate.x509.X509CertificateSerialNumberGenerator
import kotlinx.io.bytestring.ByteString
import java.math.BigInteger
import java.security.SecureRandom

internal class JavaX509CertificateSerialNumberGenerator : X509CertificateSerialNumberGenerator {

    val secureRandom = SecureRandom()

    override fun next(): ByteString {
        // 159 bits ensures a positive BigInteger under 20 bytes
        val serialNumber = BigInteger(159, secureRandom)
        return ByteString(serialNumber.toByteArray())
    }
}
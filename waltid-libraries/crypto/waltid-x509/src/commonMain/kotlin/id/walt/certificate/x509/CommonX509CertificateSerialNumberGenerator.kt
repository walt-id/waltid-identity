package id.walt.certificate.x509

import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.io.bytestring.ByteString
import kotlin.experimental.and

class CommonX509CertificateSerialNumberGenerator : X509CertificateSerialNumberGenerator {
    override fun next(): ByteString {
        val serialBytes = CryptographyRandom.nextBytes(20)
        serialBytes[0] = serialBytes[0] and 0x7F.toByte()
        return ByteString(serialBytes)
    }
}
package id.walt.certificate.x509

import kotlinx.io.bytestring.ByteString

interface X509CertificateSerialNumberGenerator {
    fun next(): ByteString
}
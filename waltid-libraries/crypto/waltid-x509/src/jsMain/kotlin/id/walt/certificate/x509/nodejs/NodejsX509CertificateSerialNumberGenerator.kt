package id.walt.certificate.x509.nodejs

import id.walt.certificate.x509.X509CertificateSerialNumberGenerator
import kotlinx.io.bytestring.ByteString
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

class NodejsX509CertificateSerialNumberGenerator : X509CertificateSerialNumberGenerator {

    override fun next(): ByteString {
        val buffer = Int8Array(20)
        NodejsCrypto.getRandomValues(buffer)
        val byteArray = ByteArray(buffer.length) { i -> buffer[i] }
        // Keep the ASN.1 INTEGER positive
        byteArray[0] = (byteArray[0].toInt() and 0x7f).toByte()
        return ByteString(byteArray)
    }
}
package id.walt.certificate.x509

import id.walt.certificate.x509.extension.Extension
import kotlinx.io.bytestring.ByteString
import kotlin.experimental.and
import kotlin.random.Random

class MockX509Certificate : X509Certificate {

    class MockX509Certificate : X509Certificate.CertificateData {
        override val version: Int
            get() = TODO("Not yet implemented")
        override val serialNumberRaw: ByteString = randomSerialNumber()

        override val issuerDn: String
            get() = TODO("Not yet implemented")
        override val validity: X509Certificate.Validity
            get() = TODO("Not yet implemented")
        override val subjectDn: String = "OU=walt.id"
        override val subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo
            get() = TODO("Not yet implemented")
        override val extensions: Map<String, Extension>
            get() = TODO("Not yet implemented")
    }


    override val data: MockX509Certificate = MockX509Certificate()

    override val signatureAlgorithmOid: String
        get() = TODO("Not yet implemented")

    override val signatureValueRaw: ByteString
        get() = TODO("Not yet implemented")

    override val fingerprintSha256: ByteString
        get() = TODO("Not yet implemented")

    override val encodedDer: ByteString
        get() = TODO("Not yet implemented")

    companion object {
        fun randomSerialNumber(): ByteString {
            val byteArray = Random.nextBytes(20)
            byteArray[0] = byteArray[0] and 0x7F.toByte() //ensure it is positive
            return ByteString(byteArray)
        }
    }
}
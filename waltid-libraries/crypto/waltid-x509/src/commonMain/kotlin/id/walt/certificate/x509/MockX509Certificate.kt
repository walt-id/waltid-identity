package id.walt.certificate.x509

import id.walt.certificate.x509.extension.Extension
import kotlinx.io.bytestring.ByteString
import kotlin.experimental.and
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Used for TrustStore testing
 * If moved to the test module, compilation fails
 */
class MockX509Certificate(private val subjectDn: String) : X509Certificate {

    inner class MockX509CertificateData : X509Certificate.CertificateData {
        override val version = 3

        override val serialNumberRaw: ByteString = randomSerialNumber()

        override val issuerDn: String
            get() = "issuerDN"

        override val validity: X509Certificate.Validity = X509Certificate.Validity(
            Clock.System.now(),
            Clock.System.now() + 3600.days
        )

        override val subjectDn: String = this@MockX509Certificate.subjectDn

        override val subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo
            get() = TODO("Not yet implemented")

        override val extensions: Map<String, Extension>
            get() = emptyMap()
    }

    override val data = MockX509CertificateData()

    override val signatureAlgorithmOid: String
        get() = ""

    override val signatureValueRaw: ByteString
        get() = ByteString()

    override val encodedDer: ByteString
        get() = ByteString()

    companion object {
        fun randomSerialNumber(): ByteString {
            val byteArray = Random.nextBytes(20)
            byteArray[0] = byteArray[0] and 0x7F.toByte() //ensure it is positive
            return ByteString(byteArray)
        }
    }
}
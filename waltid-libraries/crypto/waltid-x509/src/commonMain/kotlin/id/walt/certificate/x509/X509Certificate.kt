package id.walt.certificate.x509

import id.walt.certificate.der.ByteArrayUtil.byteStringToBase64Pem
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import kotlin.time.Instant

interface X509Certificate {

    val data: CertificateData

    val signatureAlgorithmName: String
        get() = X509SigningAlgorithmInfo.algorithmNameByOid(signatureAlgorithmOid)

    val signatureAlgorithmOid: String
    val signatureValueRaw: ByteString
    val signatureValueHex: String
        get() = signatureValueRaw.toHexString()

    val fingerprintSha256: ByteString
    val fingerprintSha256Hex: String
        get() = fingerprintSha256.toHexString()

    val encodedDer: ByteString

    val encodedPem: String
        get() = byteStringToBase64Pem(encodedDer, "CERTIFICATE")

    interface CertificateData : Pkcs10CertificateSigningRequest.RequestedCertificateData {
        val version: Int

        val serialNumberRaw: ByteString
        val serialNumberHex: String
            get() = serialNumberRaw.toHexString()

        val issuerDn: String

        val validity: Validity

    }

    data class Validity(
        val notBefore: Instant,
        val notAfter: Instant,
    )
    typealias SubjectPublicKeyInfo = Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo
}
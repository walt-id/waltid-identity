package id.walt.certificate.x509

import id.walt.certificate.der.ByteArrayUtil.byteStringToBase64Pem
import id.walt.certificate.x509.extension.ExtensionContainer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString

interface Pkcs10CertificateSigningRequest {

    val requestedCertificate: RequestedCertificateData

    val signatureAlgorithmName: String
        get() = X509SigningAlgorithmInfo.algorithmNameByOid(signatureAlgorithmOid)

    val signatureAlgorithmOid: String
    val signatureValueRaw: ByteString
    val signatureValueHex: String
        get() = signatureValueRaw.toHexString()

    val encodedDer: ByteString

    val encodedPem: String
        get() = byteStringToBase64Pem(encodedDer, "CERTIFICATE REQUEST")

    interface RequestedCertificateData : ExtensionContainer {
        val subjectDn: String
        val subjectPublicKeyInfo: SubjectPublicKeyInfo
    }

    typealias SubjectPublicKeyInfo = PublicKeyInfo
}
package id.walt.certificate.x509

import id.walt.certificate.der.ByteArrayUtil
import id.walt.certificate.der.ByteArrayUtil.byteStringToBase64Pem
import id.walt.crypto.utils.ShaUtils
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString

/**
 * Represents X.509 Subject Public Key Info specified
 * in section 4.1.2.7 of RFC 5280
 *
 * SubjectPublicKeyInfo  ::=  SEQUENCE  {
 *      algorithm            AlgorithmIdentifier,
 *      subjectPublicKey     BIT STRING  }
 */
interface PublicKeyInfo {
    val algorithmOid: String
    val algorithmName: String?
        get() = X509SigningAlgorithmInfo.algorithmNameByOid(algorithmOid)

    val ellipticCurveOid: String?

    /**
     * Contains content of subjectPublicKey (without ASN.1 tag information and length - only the content of the BIT STRING)
     */
    val keyValueRaw: ByteString
    val keyValueHex: String
        get() = keyValueRaw.toHexString()
    val keyValueBase64: String
        get() = ByteArrayUtil.byteArrayToBase64(keyValueRaw.toByteArray())

    /**
     * SHA-1 hash of publicKeyRaw (SubjectPublicKeyInfo.subjectPublicKey)
     */
    val keyId: ByteString
        get() = ByteString(ShaUtils.sha1(keyValueRaw.toByteArray()))

    /**
     * X509 SubjectPublicKeyInfo encoded as DER
     */
    val encodedDer: ByteString

    val encodedPem: String
        get() = byteStringToBase64Pem(encodedDer, "PUBLIC KEY")
}

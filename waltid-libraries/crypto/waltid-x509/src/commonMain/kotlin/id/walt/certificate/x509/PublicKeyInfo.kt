package id.walt.certificate.x509

import id.walt.certificate.der.ByteArrayUtil
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
    val publicKeyRaw: ByteString
    val publicKeyHex: String
        get() = publicKeyRaw.toHexString()
    val publicKeyBase64: String
        get() = ByteArrayUtil.byteArrayToBase64(publicKeyRaw.toByteArray())

    /**
     * SHA-1 hash of publicKeyRaw (SubjectPublicKeyInfo.subjectPublicKey)
     */
    val keyId: ByteString
        get() = ByteString(ShaUtils.sha1(publicKeyRaw.toByteArray()))
}

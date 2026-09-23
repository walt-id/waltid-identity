package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.X509SigningAlgorithmInfo.Companion.KEY_ALG_OID_RSA
import kotlinx.io.bytestring.ByteString

internal class SignumPublicKeyInfo private constructor(
    val keyInfo: CryptoPublicKey,
) : PublicKeyInfo {

    override val algorithmOid: String = keyInfo.oid.toString()
    override val ellipticCurveOid: String? = (keyInfo as? CryptoPublicKey.EC)?.curve?.oid?.toString()
    override val keyValueRaw: ByteString = ByteString(keyInfo.iosEncoded)

    override val rsaKeyLengthBits: Int?
        get() = if (algorithmOid == KEY_ALG_OID_RSA) {
            (keyInfo as CryptoPublicKey.RSA).bits.number.toInt()
        } else {
            null
        }

    override val encodedDer: ByteString
        get() = ByteString(keyInfo.encodeToDer())

    companion object {

        fun ofDerEncoded(derEncodedSubjectPublicKeyInfo: ByteArray): SignumPublicKeyInfo =
            ofCryptoPublicKey(CryptoPublicKey.decodeFromDer(derEncodedSubjectPublicKeyInfo))

        fun ofCryptoPublicKey(publicKeyInfo: CryptoPublicKey): SignumPublicKeyInfo {
            return SignumPublicKeyInfo(publicKeyInfo)
        }

    }
}
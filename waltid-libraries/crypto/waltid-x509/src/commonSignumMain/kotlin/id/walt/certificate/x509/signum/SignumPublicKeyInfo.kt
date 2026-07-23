package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import id.walt.certificate.x509.PublicKeyInfo
import kotlinx.io.bytestring.ByteString

internal class SignumPublicKeyInfo private constructor(
    private val keyInfo: CryptoPublicKey,
) : PublicKeyInfo {

    override val algorithmOid: String = keyInfo.oid.toString()
    override val ellipticCurveOid: String? = (keyInfo as? CryptoPublicKey.EC)?.curve?.oid?.toString()
    override val keyValueRaw: ByteString = ByteString(keyInfo.iosEncoded)
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
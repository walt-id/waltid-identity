package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import id.walt.certificate.x509.PublicKeyInfo
import kotlinx.io.bytestring.ByteString

internal class SignumPublicKeyInfo private constructor(
    override val algorithmOid: String,
    override val ellipticCurveOid: String?,
    override val publicKeyRaw: ByteString
) : PublicKeyInfo {


    companion object {

        fun ofDerEncoded(derEncodedSubjectPublicKeyInfo: ByteArray): SignumPublicKeyInfo =
            ofCryptoPublicKey(CryptoPublicKey.decodeFromDer(derEncodedSubjectPublicKeyInfo))

        fun ofCryptoPublicKey(publicKeyInfo: CryptoPublicKey): SignumPublicKeyInfo {
            val algorithmOid = publicKeyInfo.oid
            val curveOid = (publicKeyInfo as? CryptoPublicKey.EC)?.curve?.oid?.toString()
            return SignumPublicKeyInfo(algorithmOid.toString(), curveOid, ByteString(publicKeyInfo.iosEncoded))
        }

    }
}
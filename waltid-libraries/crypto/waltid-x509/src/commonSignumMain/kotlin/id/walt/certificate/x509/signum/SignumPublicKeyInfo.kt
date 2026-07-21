package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.parse
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


        fun ofDerEncoded2(derEncodedSubjectPublicKeyInfo: ByteArray): SignumPublicKeyInfo {
            val subjectPublicKeyInfo = Asn1Element.parse(derEncodedSubjectPublicKeyInfo)
                .asSequence()
            val algorithm = subjectPublicKeyInfo.children[0].asSequence()
            val algorithmOid =
                ObjectIdentifier.decodeFromAsn1ContentBytes(algorithm.children[0].asPrimitive().content).toString()
            val ellipticCurveOid =
                algorithm.children[1].asPrimitive().let { it ->
                    if (it.tag == Asn1Element.Tag.OID) {
                        ObjectIdentifier.decodeFromAsn1ContentBytes(it.content).toString()
                    } else {
                        null
                    }
                }
            val keyDerBitString = subjectPublicKeyInfo.children[1].asPrimitive().content
            require(keyDerBitString[0] == 0.toByte()) { "Number of unsued bits is not 0, this is not supported" }
            val keyRaw = keyDerBitString.copyOfRange(1, keyDerBitString.size)
            return SignumPublicKeyInfo(algorithmOid, ellipticCurveOid, ByteString(keyRaw))
        }
    }
}
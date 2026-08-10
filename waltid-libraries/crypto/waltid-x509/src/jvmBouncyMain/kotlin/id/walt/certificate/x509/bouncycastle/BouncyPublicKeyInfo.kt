package id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.PublicKeyInfo
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo as BouncySpki

class BouncyPublicKeyInfo(
    spki: BouncySpki
) : PublicKeyInfo {
    override val algorithmOid: String = spki.algorithm.algorithm.id

    override val ellipticCurveOid: String? =
        (spki.algorithm.parameters as? ASN1ObjectIdentifier)?.toString()

    override val keyValueRaw: ByteString =
        ByteString(spki.publicKeyData.bytes)

    override val rsaKeyLengthBits: Int?
        get() = TODO("Not yet implemented - signature validation done with Bouncy Castle Backend, this method should not be needed")

    override val encodedDer: ByteString = ByteString(spki.encoded)
}
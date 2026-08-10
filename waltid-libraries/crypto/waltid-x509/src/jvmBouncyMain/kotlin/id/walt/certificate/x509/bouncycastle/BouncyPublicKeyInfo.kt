package id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.X509SigningAlgorithmInfo.Companion.KEY_ALG_OID_RSA
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.RSAPublicKey
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo as BouncySpki

class BouncyPublicKeyInfo(
    private val spki: BouncySpki
) : PublicKeyInfo {
    override val algorithmOid: String = spki.algorithm.algorithm.id

    override val ellipticCurveOid: String? =
        (spki.algorithm.parameters as? ASN1ObjectIdentifier)?.toString()

    override val keyValueRaw: ByteString =
        ByteString(spki.publicKeyData.bytes)

    override val rsaKeyLengthBits: Int?
        get() = if (algorithmOid == KEY_ALG_OID_RSA) {
            val rsa = RSAPublicKey.getInstance(spki.publicKeyData.bytes)
            return rsa.modulus.bitLength()
        } else {
            null
        }

    override val encodedDer: ByteString = ByteString(spki.encoded)
}
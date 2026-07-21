package id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.PublicKeyInfo
import kotlinx.io.bytestring.ByteString

data class BouncyPublicKeyInfo(
    override val algorithmOid: String,
    override val ellipticCurveOid: String?,
    override val publicKeyRaw: ByteString,
) : PublicKeyInfo {
}
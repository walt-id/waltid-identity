package id.walt.certificate.x509

import id.walt.certificate.x509.bouncycastle.BouncyPublicKeyInfo
import id.walt.crypto.keys.Key
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import kotlin.io.encoding.Base64

object BouncyPublicKeyInfoUtil {

    suspend fun publicKeyInfoOfKey(keyPair: Key): PublicKeyInfo {
        // publicKey.getPublicKeyRepresentation() doesn't work for EC keys
        // so we use the PEM to get to the key bytes
        val publicKey = keyPair.getPublicKey()
        val publicKeyPem = publicKey.exportPEM()
        val keyInfo = parsePublicKeyPem(publicKeyPem)
        return BouncyPublicKeyInfo(
            keyInfo.algorithm.algorithm.id,
            keyInfo.algorithm?.parameters
                ?.let { it as? ASN1ObjectIdentifier }
                ?.let { p ->
                    ASN1ObjectIdentifier.getInstance(p).id
                },
            ByteString(keyInfo.publicKeyData.bytes),
        )
    }

    val PublicKeyInfo.bouncyCastleAlgorithmIdentifier: AlgorithmIdentifier
        get() = AlgorithmIdentifier(
            ASN1ObjectIdentifier(algorithmOid),
            ellipticCurveOid?.let { ASN1ObjectIdentifier(it) })

    val PublicKeyInfo.bouncyCastleSubjectPublicKeyInfo: SubjectPublicKeyInfo
        get() = SubjectPublicKeyInfo(
            bouncyCastleAlgorithmIdentifier,
            this.publicKeyRaw.toByteArray()
        )

    private val pemHeaderFooterRegx = Regex("(^-+[A-Z\\s]+-+\\s*$)|\\s+", RegexOption.MULTILINE)

    fun parsePublicKeyPem(publicKeyPem: String): SubjectPublicKeyInfo {
        try {
            // org.bouncycastle.openssl.PEMParser seems to have some issues
            // decode manually
            val base64 = publicKeyPem.replace(pemHeaderFooterRegx, "").trim()
            val asn1encoded = Base64.decode(base64)
            return ASN1InputStream(asn1encoded).use {
                val asn1Data = it.readObject()
                SubjectPublicKeyInfo.getInstance(asn1Data)
            }
        } catch (e: Exception) {
            throw RuntimeException("Could not parse public key info from $publicKeyPem", e)
        }
    }
}

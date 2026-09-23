package id.walt.certificate.x509

import id.walt.certificate.x509.bouncycastle.BouncyPublicKeyInfo
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyEncodingFormat
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import kotlin.io.encoding.Base64
import id.walt.crypto.keys.Key as Crypto1Key

object BouncyPublicKeyInfoUtil {

    suspend fun publicKeyInfoOfKey(publicKey: Key): PublicKeyInfo {

        val encoded = publicKey.capabilities
            .publicKeyExporter
            ?.exportPublicKey(format = KeyEncodingFormat.SPKI_DER)
            ?: throw IllegalArgumentException("X.509 key must support public-key export")
        check(encoded is EncodedKey.SpkiDer) { "X.509 key was not export as SPKI, format was: ${encoded.encodingFormat}" }
        val spki = SubjectPublicKeyInfo.getInstance(encoded.data.toByteArray())
        return BouncyPublicKeyInfo(spki)
    }

    suspend fun publicKeyInfoOfKey(keyPair: Crypto1Key): PublicKeyInfo {
        // publicKey.getPublicKeyRepresentation() doesn't work for EC keys
        // so we use the PEM to get to the key bytes
        val publicKey = keyPair.getPublicKey()
        val publicKeyPem = publicKey.exportPEM()
        val keyInfo = parsePublicKeyPem(publicKeyPem)
        return BouncyPublicKeyInfo(keyInfo)
    }

    val PublicKeyInfo.bouncyCastleAlgorithmIdentifier: AlgorithmIdentifier
        get() = AlgorithmIdentifier(
            ASN1ObjectIdentifier(algorithmOid),
            ellipticCurveOid?.let { ASN1ObjectIdentifier(it) })

    val PublicKeyInfo.bouncyCastleSubjectPublicKeyInfo: SubjectPublicKeyInfo
        get() = SubjectPublicKeyInfo(
            bouncyCastleAlgorithmIdentifier,
            this.keyValueRaw.toByteArray()
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

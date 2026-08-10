package id.walt.certificate.x509.signum

import id.walt.certificate.x509.PublicKeyInfo
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyEncodingFormat
import kotlin.io.encoding.Base64
import id.walt.crypto.keys.Key as Crypto1Key

object SignumPublicKeyInfoUtil {

    suspend fun publicKeyInfoOfKey(key: Key): PublicKeyInfo {
        val encoded = key.capabilities.publicKeyExporter?.exportPublicKey(format = KeyEncodingFormat.SPKI_DER)
        require(encoded != null) { "Key with id '${key.id}' does not support public key export" }
        return SignumPublicKeyInfo.ofDerEncoded(encoded.data.toByteArray())
    }

    suspend fun publicKeyInfoOfKey(key: Crypto1Key): PublicKeyInfo {
        val publicKeyPem = key.getPublicKey().exportPEM()
        return parsePublicKeyPem(publicKeyPem)
    }

    private val pemHeaderFooterRegx = Regex("(^-+[A-Z\\s]+-+\\s*$)|\\s+", RegexOption.MULTILINE)

    private fun parsePublicKeyPem(publicKeyPem: String): SignumPublicKeyInfo {
        try {
            val base64 = publicKeyPem.replace(pemHeaderFooterRegx, "").trim()
            val asn1encoded = Base64.decode(base64)
            return SignumPublicKeyInfo.ofDerEncoded(asn1encoded)
        } catch (e: Exception) {
            throw RuntimeException("Could not parse public key info from $publicKeyPem", e)
        }
    }
}
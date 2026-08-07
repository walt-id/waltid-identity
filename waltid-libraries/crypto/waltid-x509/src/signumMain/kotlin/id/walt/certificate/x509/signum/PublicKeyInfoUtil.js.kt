package id.walt.certificate.x509.signum

import id.walt.certificate.x509.PublicKeyInfo
import id.walt.crypto.keys.Key
import kotlin.io.encoding.Base64

object SignumPublicKeyInfoUtil {


    suspend fun publicKeyInfoOfKey(keyPair: Key): PublicKeyInfo {
        val publicKeyPem = keyPair.getPublicKey().exportPEM()
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
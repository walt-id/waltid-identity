package id.walt.certificate.x509

import at.asitplus.signum.indispensable.CryptoPublicKey
import id.walt.crypto.keys.Key
import kotlin.io.encoding.Base64

object SignumPublicKeyInfoUtil {

    suspend fun Key.toSignumPublicKey(): CryptoPublicKey =
        publicKeyInfoOfKey(this)

    suspend fun publicKeyInfoOfKey(keyPair: Key): CryptoPublicKey {
        // publicKey.getPublicKeyRepresentation() doesn't work for EC keys
        // so we use the PEM to get to the key bytes
        val publicKey = keyPair.getPublicKey()
        val publicKeyPem = publicKey.exportPEM()
        return parsePublicKeyPem(publicKeyPem)
    }

    private val pemHeaderFooterRegx = Regex("(^-+[A-Z\\s]+-+\\s*$)|\\s+", RegexOption.MULTILINE)

    fun parsePublicKeyPem(publicKeyPem: String): CryptoPublicKey {
        try {
            // org.bouncycastle.openssl.PEMParser seems to have some issues
            // decode manually
            val base64 = publicKeyPem.replace(pemHeaderFooterRegx, "").trim()
            val derEncoded: ByteArray = Base64.decode(base64)
            return CryptoPublicKey.decodeFromDer(derEncoded)
        } catch (e: Exception) {
            throw RuntimeException("Could not parse public key info from $publicKeyPem", e)
        }
    }
}

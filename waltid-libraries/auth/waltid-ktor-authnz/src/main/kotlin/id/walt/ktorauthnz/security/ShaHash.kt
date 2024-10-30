package id.walt.ktorauthnz.security

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA3_512
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString

object ShaHash {

    val sha3_512 = CryptographyProvider.Default.get(SHA3_512).hasher()

    /**
     * SHA-3 512 bit
     */
    suspend fun hash3Sha512Hex(text: ByteString): ByteString = sha3_512.hash(text)
    suspend fun hash3Sha512Hex(text: String): ByteString = hash3Sha512Hex(text.encodeToByteString())

}

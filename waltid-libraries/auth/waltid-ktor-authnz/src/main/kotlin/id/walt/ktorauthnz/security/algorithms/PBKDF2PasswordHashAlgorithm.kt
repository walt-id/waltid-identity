package id.walt.ktorauthnz.security.algorithms

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA512
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.bytestring.toHexString

class PBKDF2PasswordHashAlgorithm : PasswordHashAlgorithm() {
    val pbkdf2 = CryptographyProvider.Default.get(PBKDF2)

    suspend fun hashInternal(password: String, salt: ByteString): ByteString {
        val sd = pbkdf2.secretDerivation(SHA512, 10, 256.bits, salt)
        return sd.deriveSecret(password.encodeToByteString())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun hash(password: String, salt: ByteString): String =
        hashInternal(password, salt).toHexString()
}

package id.walt.walletdemo.compose.logic

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64

@OptIn(CryptographyProviderApi::class)
internal class PersistentDemoPinStore(
    private val readRecord: () -> String?,
    private val writeRecord: (String) -> Unit,
    private val provider: CryptographyProvider = CryptographyProvider.Default,
) : DemoPinStore {
    private val pbkdf2 by lazy { provider.get(PBKDF2) }

    override fun hasPin(): Boolean = readRecord() != null

    override suspend fun setPin(pin: String) {
        val salt = CryptographyRandom.nextBytes(SALT_SIZE_BYTES)
        val verifier = derive(pin, salt, ITERATIONS)
        writeRecord(
            listOf(
                RECORD_VERSION,
                ITERATIONS.toString(),
                Base64.encode(salt),
                Base64.encode(verifier),
            ).joinToString(RECORD_SEPARATOR),
        )
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val record = readRecord() ?: return false
        val parts = record.split(RECORD_SEPARATOR, limit = 4)
        require(parts.size == 4 && parts[0] == RECORD_VERSION) { "Unsupported PIN verifier record" }

        val iterations = parts[1].toInt()
        require(iterations in 1..MAX_ITERATIONS) { "Invalid PIN verifier iteration count" }
        val salt = Base64.decode(parts[2])
        val expected = Base64.decode(parts[3])
        require(salt.size == SALT_SIZE_BYTES && expected.size == VERIFIER_SIZE_BYTES) {
            "Invalid PIN verifier record"
        }

        return derive(pin, salt, iterations).constantTimeEquals(expected)
    }

    private suspend fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        pbkdf2.secretDerivation(
            digest = SHA256,
            iterations = iterations,
            outputSize = VERIFIER_SIZE_BYTES.bytes,
            salt = salt,
        ).deriveSecretToByteArray(pin.encodeToByteArray())

    private fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
        var difference = size xor other.size
        for (index in indices) {
            difference = difference or (this[index].toInt() xor other[index].toInt())
        }
        return difference == 0
    }

    private companion object {
        const val RECORD_VERSION = "1"
        const val RECORD_SEPARATOR = ":"
        const val ITERATIONS = 210_000
        const val MAX_ITERATIONS = 1_000_000
        const val SALT_SIZE_BYTES = 16
        const val VERIFIER_SIZE_BYTES = 32
    }
}

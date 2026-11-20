package id.walt.ktorauthnz.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Reads and writes the fixed Argon2 v19 profile used for stored wallet passwords
 * and Enterprise API keys. The algorithm identifier is part of the persisted format.
 */
class Argon2Phc(variant: Variant, private val iterations: Int) {
    enum class Variant(internal val identifier: String, internal val type: Int) {
        I("argon2i", Argon2Parameters.ARGON2_i),
        ID("argon2id", Argon2Parameters.ARGON2_id),
    }

    init {
        require(iterations > 0)
    }

    private val type = variant.type
    private val prefix = "$${variant.identifier}\$v=19\$m=${MEMORY_KIB},t=${iterations},p=1\$"

    fun hash(secret: ByteArray): String =
        encode(secret, ByteArray(SALT_BYTES).also(random::nextBytes))

    fun verify(secret: ByteArray, encoded: String): Boolean {
        if (!encoded.startsWith(prefix) ||
            encoded.length != prefix.length + ENCODED_SALT_LENGTH + 1 + ENCODED_TAG_LENGTH ||
            encoded[prefix.length + ENCODED_SALT_LENGTH] != '$'
        ) {
            return false
        }
        val salt = try {
            decoder.decode(encoded.substring(prefix.length, prefix.length + ENCODED_SALT_LENGTH))
        } catch (_: IllegalArgumentException) {
            return false
        }
        val expected = encode(secret, salt)
        return MessageDigest.isEqual(expected.encodeToByteArray(), encoded.encodeToByteArray())
    }

    private fun encode(secret: ByteArray, salt: ByteArray): String {
        val parameters = Argon2Parameters.Builder(type)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(MEMORY_KIB)
            .withIterations(iterations)
            .withParallelism(1)
            .withSalt(salt)
            .build()
        val tag = ByteArray(TAG_BYTES)
        return try {
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(secret, tag)
            "$prefix${encoder.encodeToString(salt)}\$${encoder.encodeToString(tag)}"
        } finally {
            tag.fill(0)
        }
    }

    private companion object {
        const val MEMORY_KIB = 65_536
        const val SALT_BYTES = 16
        const val TAG_BYTES = 32
        const val ENCODED_SALT_LENGTH = 22
        const val ENCODED_TAG_LENGTH = 43
        val random = SecureRandom()
        val encoder = Base64.getEncoder().withoutPadding()
        val decoder = Base64.getDecoder()
    }
}

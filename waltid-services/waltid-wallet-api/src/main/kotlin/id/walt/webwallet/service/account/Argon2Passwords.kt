package id.walt.webwallet.service.account

import com.password4j.Argon2Function
import com.password4j.types.Argon2
import java.security.SecureRandom

/**
 * Argon2i parameters match hashes previously produced for wallet accounts:
 * version 19, 64 MiB, 10 iterations, parallelism 1, 16-byte salt, 32-byte tag.
 */
internal object Argon2Passwords {
    private const val SALT_LENGTH = 16
    private const val HASH_LENGTH = 32
    private const val MEMORY_KIB = 65_536
    private const val PARALLELISM = 1
    private const val ITERATIONS = 10

    private val secureRandom = SecureRandom()
    private val hasher = Argon2Function.getInstance(
        MEMORY_KIB,
        ITERATIONS,
        PARALLELISM,
        HASH_LENGTH,
        Argon2.I,
    )

    fun hash(password: ByteArray): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return try {
            hasher.hash(password, salt).result
        } finally {
            password.fill(0)
            salt.fill(0)
        }
    }

    fun verify(encodedHash: String, password: ByteArray): Boolean =
        try {
            Argon2Function.getInstanceFromHash(encodedHash)
                .check(password, encodedHash.toByteArray(Charsets.UTF_8))
        } finally {
            password.fill(0)
        }
}

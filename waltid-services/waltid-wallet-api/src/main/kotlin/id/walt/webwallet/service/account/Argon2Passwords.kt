package id.walt.webwallet.service.account

import id.walt.ktorauthnz.security.Argon2Phc

/**
 * Argon2i parameters match hashes previously produced for wallet accounts:
 * version 19, 64 MiB, 10 iterations, parallelism 1, 16-byte salt, 32-byte tag.
 */
internal object Argon2Passwords {
    private val hasher = Argon2Phc(Argon2Phc.Variant.I, iterations = 10)

    fun hash(password: ByteArray): String =
        try {
            hasher.hash(password)
        } finally {
            password.fill(0)
        }

    fun verify(encodedHash: String, password: ByteArray): Boolean =
        try {
            hasher.verify(password, encodedHash)
        } finally {
            password.fill(0)
        }
}

package id.walt.ktorauthnz.security

import com.password4j.Argon2Function
import com.password4j.BcryptFunction
import com.password4j.CompressedPBKDF2Function
import com.password4j.Password
import com.password4j.ScryptFunction
import id.walt.ktorauthnz.KtorAuthnzManager.passwordHashingConfig as config

object PasswordHashing {
    private const val BCRYPT_MAX_BYTES = 72L

    fun hash(password: String): PasswordHash = hash(password, config.selectedPwHashAlgorithm, config.pepper)

    private fun hash(password: String, algorithm: PasswordHashingAlgorithm, pepper: String): PasswordHash {
        val builder = Password.hash(password).addPepper(pepper)
        // Bcrypt generates its own versioned salt when none is supplied.
        if (algorithm == PasswordHashingAlgorithm.BCRYPT) {
            require(fitsBcrypt(password, pepper)) { "Bcrypt accepts up to 72 UTF-8 bytes including the pepper" }
        } else {
            builder.addRandomSalt()
        }
        return PasswordHash(builder.with(algorithm.algorithmInstance.value).result, algorithm)
    }

    fun needsEvolving(storedHash: PasswordHash) =
        config.selectedHashConversions.containsKey(storedHash.algorithm) ||
                (config.selectedHashConversions.containsKey(null) && config.selectedHashConversions[null] != storedHash.algorithm)

    fun evolvesTo(storedHashAlgorithm: PasswordHashingAlgorithm): PasswordHashingAlgorithm =
        config.selectedHashConversions[storedHashAlgorithm] ?: config.selectedHashConversions[null]
        ?: error("Does not need evolving: $storedHashAlgorithm")

    data class CheckResult(
        val valid: Boolean,
        val updated: Boolean,
        val updatedHash: PasswordHash? = null,
    )

    fun check(passwordToCheck: String, storedHash: PasswordHash): CheckResult {
        val pepper = config.pepper
        val verifier = when (storedHash.algorithm) {
            PasswordHashingAlgorithm.BCRYPT -> BcryptFunction.getInstanceFromHash(storedHash.hash)
            PasswordHashingAlgorithm.ARGON2 -> Argon2Function.getInstanceFromHash(storedHash.hash)
            PasswordHashingAlgorithm.PBKDF2_COMPRESSED -> CompressedPBKDF2Function.getInstanceFromHash(storedHash.hash)
            PasswordHashingAlgorithm.SCRYPT -> ScryptFunction.getInstanceFromHash(storedHash.hash)
            else -> storedHash.algorithm.algorithmInstance.value
        }
        val valid = Password.check(passwordToCheck, storedHash.hash)
            .addPepper(pepper)
            .with(verifier)
        if (!valid || !needsEvolving(storedHash)) return CheckResult(valid, false)

        val target = evolvesTo(storedHash.algorithm)
        // These Password4j results keep the salt separately. The existing record stores only the result.
        if (target == PasswordHashingAlgorithm.PBKDF2 || target == PasswordHashingAlgorithm.MESSAGE_DIGEST ||
            target == PasswordHashingAlgorithm.BALLON_HASHING) return CheckResult(true, false)

        // Only migrate when both algorithms authenticate the entire submitted secret.
        val usesBcrypt = target == PasswordHashingAlgorithm.BCRYPT || storedHash.algorithm == PasswordHashingAlgorithm.BCRYPT
        if (usesBcrypt) {
            val bytes = bcryptInputBytes(passwordToCheck, pepper)
            // At the limit a legacy bcrypt record also matches passwords with a longer suffix.
            val ambiguousPrefix = storedHash.algorithm == PasswordHashingAlgorithm.BCRYPT && bytes == BCRYPT_MAX_BYTES
            if (bytes > BCRYPT_MAX_BYTES || ambiguousPrefix) {
                return CheckResult(true, false)
            }
        }
        val updated = hash(passwordToCheck, target, pepper)
        return CheckResult(true, true, updated)
    }

    fun fitsBcrypt(password: String, pepper: String): Boolean =
        bcryptInputBytes(password, pepper) <= BCRYPT_MAX_BYTES

    private fun bcryptInputBytes(password: String, pepper: String): Long =
        password.encodeToByteArray().size.toLong() + pepper.encodeToByteArray().size
}

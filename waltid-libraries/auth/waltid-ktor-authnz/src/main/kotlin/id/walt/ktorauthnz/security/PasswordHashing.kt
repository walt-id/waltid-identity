package id.walt.ktorauthnz.security

import com.password4j.Argon2Function
import com.password4j.BcryptFunction
import com.password4j.CompressedPBKDF2Function
import com.password4j.HashingFunction
import com.password4j.Password
import com.password4j.ScryptFunction
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.KtorAuthnzManager.passwordHashingConfig as config
import id.walt.ktorauthnz.accounts.VersionedPasswordPepperStore

object PasswordHashing {
    private const val BCRYPT_MAX_BYTES = 72L
    private const val CONFIGURED_PEPPER_TAG = "p1:"

    private val versionedPasswordStore: VersionedPasswordPepperStore?
        get() = if (KtorAuthnzManager.isAccountStoreConfigured) {
            KtorAuthnzManager.accountStore as? VersionedPasswordPepperStore
        } else null

    fun hash(password: String): PasswordHash = hash(password, config.selectedPwHashAlgorithm)

    private fun hash(password: String, algorithm: PasswordHashingAlgorithm): PasswordHash {
        val store = versionedPasswordStore
        require(store == null || algorithm != PasswordHashingAlgorithm.NONE) { "A password hash algorithm is required" }
        val pepper = if (store?.writeUnmarkedPasswords == true) store.unmarkedPasswordPepper else config.pepper
        require(!algorithm.needsSeparateSalt) {
            "$algorithm cannot store its salt in the password record. Choose ARGON2, BCRYPT, SCRYPT or PBKDF2_COMPRESSED."
        }
        val builder = Password.hash(password).addPepper(pepper)
        // Bcrypt generates its own versioned salt when none is supplied.
        if (algorithm == PasswordHashingAlgorithm.BCRYPT) {
            require(fitsBcrypt(password, pepper)) { "Bcrypt accepts up to 72 UTF-8 bytes including the pepper" }
        } else {
            builder.addRandomSalt()
        }
        val encoded = builder.with(algorithm.algorithmInstance.value).result
        val tag = if (store != null && pepper != store.unmarkedPasswordPepper) CONFIGURED_PEPPER_TAG else ""
        return PasswordHash(tag + encoded, algorithm)
    }

    private val PasswordHashingAlgorithm.needsSeparateSalt: Boolean
        get() = this == PasswordHashingAlgorithm.PBKDF2 || this == PasswordHashingAlgorithm.MESSAGE_DIGEST ||
                this == PasswordHashingAlgorithm.BALLON_HASHING

    private data class Credential(val stored: PasswordHash, val pepper: String)

    private fun credential(stored: PasswordHash): Credential {
        val store = versionedPasswordStore ?: return Credential(stored, config.pepper)
        return if (stored.hash.startsWith(CONFIGURED_PEPPER_TAG)) {
            Credential(stored.copy(hash = stored.hash.removePrefix(CONFIGURED_PEPPER_TAG)), config.pepper)
        } else {
            Credential(stored, store.unmarkedPasswordPepper)
        }
    }

    fun needsEvolving(storedHash: PasswordHash) = migrationTarget(credential(storedHash)) != null

    private fun migrationTarget(credential: Credential, verifier: HashingFunction? = null): PasswordHashingAlgorithm? {
        val stored = credential.stored
        val pepperChanged = credential.pepper != config.pepper
        val rules = config.selectedHashConversions
        val target = rules[stored.algorithm] ?: rules[null]?.takeUnless { it == stored.algorithm && !pepperChanged } ?: return null
        if (target.needsSeparateSalt || target == PasswordHashingAlgorithm.NONE) return null
        if (target == stored.algorithm && !pepperChanged && (verifier ?: verifierFor(stored)) == target.algorithmInstance.value) return null
        return target
    }

    fun evolvesTo(storedHashAlgorithm: PasswordHashingAlgorithm): PasswordHashingAlgorithm =
        config.selectedHashConversions[storedHashAlgorithm] ?: config.selectedHashConversions[null]
        ?: error("Does not need evolving: $storedHashAlgorithm")

    data class CheckResult(
        val valid: Boolean,
        val updated: Boolean,
        val updatedHash: PasswordHash? = null,
    )

    private fun verifierFor(storedHash: PasswordHash): HashingFunction =
        when (storedHash.algorithm) {
            PasswordHashingAlgorithm.BCRYPT -> BcryptFunction.getInstanceFromHash(storedHash.hash)
            PasswordHashingAlgorithm.ARGON2 -> Argon2Function.getInstanceFromHash(storedHash.hash)
            PasswordHashingAlgorithm.PBKDF2_COMPRESSED -> CompressedPBKDF2Function.getInstanceFromHash(storedHash.hash)
            PasswordHashingAlgorithm.SCRYPT -> ScryptFunction.getInstanceFromHash(storedHash.hash)
            else -> storedHash.algorithm.algorithmInstance.value
        }

    fun check(passwordToCheck: String, storedHash: PasswordHash): CheckResult =
        check(passwordToCheck, storedHash, allowUpgrade = true)

    internal fun check(passwordToCheck: String, storedHash: PasswordHash, allowUpgrade: Boolean): CheckResult {
        val credential = credential(storedHash)
        val verifier = verifierFor(credential.stored)
        val valid = Password.check(passwordToCheck, credential.stored.hash)
            .addPepper(credential.pepper)
            .with(verifier)
        if (!valid || !allowUpgrade) return CheckResult(valid, false)
        val target = migrationTarget(credential, verifier) ?: return CheckResult(true, false)

        // Only migrate when both algorithms authenticate the entire submitted secret.
        // At its limit a legacy bcrypt record can also match a longer password.
        if (storedHash.algorithm == PasswordHashingAlgorithm.BCRYPT &&
            bcryptInputBytes(passwordToCheck, credential.pepper) >= BCRYPT_MAX_BYTES) return CheckResult(true, false)
        if (target == PasswordHashingAlgorithm.BCRYPT && !fitsBcrypt(passwordToCheck, config.pepper)) return CheckResult(true, false)
        val updated = hash(passwordToCheck, target)
        return CheckResult(true, true, updated)
    }

    fun fitsBcrypt(password: String, pepper: String): Boolean =
        bcryptInputBytes(password, pepper) <= BCRYPT_MAX_BYTES

    private fun bcryptInputBytes(password: String, pepper: String): Long =
        password.encodeToByteArray().size.toLong() + pepper.encodeToByteArray().size
}

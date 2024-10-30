package id.walt.ktorauthnz.security

import com.password4j.Password
import id.walt.ktorauthnz.KtorAuthnzManager.passwordHashingConfig as config

object PasswordHashing {

    private val selectedPwHashFunction
        get() = config.selectedPwHashAlgorithm.algorithmInstance.value

    @Deprecated("Use hash2", replaceWith = ReplaceWith("hash2"))
    fun hash(password: String): String =
        Password.hash(password)
            .addRandomSalt()
            .addPepper(config.pepper)
            .with(selectedPwHashFunction)
            .result

    fun hash2(password: String): PasswordHash =
        Password.hash(password)
            .addRandomSalt()
            .addPepper(config.pepper)
            .with(selectedPwHashFunction)
            .let { PasswordHash(it.result, config.selectedPwHashAlgorithm) }

    fun needsEvolving(storedHash: PasswordHash) =
        config.selectedHashConversions.containsKey(storedHash.algorithm)

    fun evolvesTo(storedHashAlgorithm: PasswordHashingAlgorithm): PasswordHashingAlgorithm =
        config.selectedHashConversions[storedHashAlgorithm] ?: error("Does not need evolving: ${storedHashAlgorithm}")

    data class CheckResult(
        val valid: Boolean,
        val updated: Boolean,
        val updatedHash: PasswordHash? = null,
    )

    fun check(passwordToCheck: String, storedHash: PasswordHash): CheckResult =
        when {
            needsEvolving(storedHash) -> Password.check(passwordToCheck, storedHash.hash)
                .addPepper(config.pepper)
                .andUpdate()
                .addNewRandomSalt()
                .addNewPepper(config.pepper)
                .with(selectedPwHashFunction, evolvesTo(storedHash.algorithm).algorithmInstance.value)
                .let { CheckResult(it.isVerified, it.isUpdated, it.hash?.let { hash -> PasswordHash.fromUpdate(hash) }) }

            else -> Password.check(passwordToCheck, storedHash.hash)
                .addPepper(config.pepper)
                .with(storedHash.algorithm.algorithmInstance.value)
                .let { CheckResult(it, false, null) }
        }

}



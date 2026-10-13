package id.walt.ktorauthnz.security

import kotlinx.serialization.Serializable

@Serializable
data class PasswordHashingConfiguration(

    /**
     * A secret of your deployment mixed into every password hash, kept out of the database (e.g. in an environment
     * variable), so that a leaked database alone does not allow guessing passwords. Set your own: the default is
     * public, and stays for hashes made with it - which only verify with the pepper they were made with.
     */
    val pepper: String = DEFAULT_PEPPER,
    var selectedPwHashAlgorithm: PasswordHashingAlgorithm = PasswordHashingAlgorithm.ARGON2,
    var selectedHashConversions: Map<PasswordHashingAlgorithm?, PasswordHashingAlgorithm> = mapOf(
        null to PasswordHashingAlgorithm.ARGON2
    ),
) {
    companion object {
        const val DEFAULT_PEPPER = "waltid-ktor-authnz"
    }
}

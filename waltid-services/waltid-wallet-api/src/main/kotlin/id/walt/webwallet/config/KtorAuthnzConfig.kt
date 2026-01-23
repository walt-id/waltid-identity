package id.walt.webwallet.config

import id.walt.crypto.keys.KeyManager
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.security.PasswordHashingAlgorithm
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import org.intellij.lang.annotations.Language

@Language("JSON")
private const val defaultAuthFlowJson = """
{
  "method": "email",
  "success": true
}
"""

@Serializable
data class KtorAuthnzConfig(
    val requireHttps: Boolean,

    val pepper: String,

    /** Hash algorithm to use for passwords for signing */
    val hashAlgorithm: PasswordHashingAlgorithm,

    val authFlow: AuthFlow? = null,
    val authFlows: List<AuthFlow>? = listOf(AuthFlow.fromConfig(defaultAuthFlowJson)),

    /**
     * If previously you used other (older) password hash algorithms, you
     * can use this function to migrate old hashes to new hash algorithms. This
     * works at login-time: When a user logs in with a password that uses a hash algorithm
     * on this list, the password will be re-hashed in the specified replacement algorithm.
     * If null is used as hash algorithm selector, all algorithms expect for the target
     * algorithm will be converted automatically.
     * */
    val hashMigrations: Map<PasswordHashingAlgorithm?, PasswordHashingAlgorithm> = emptyMap(),

    val tokenType: AuthnzTokens = AuthnzTokens.STORE_IN_MEMORY,

    /** (waltid-crypto) Key for signing the login token */
    val signingKey: JsonObject? = null,
    /** (waltid-crypto) Key for verifying received login tokens */
    val verificationKey: JsonObject? = null,

    val cookieDomain: String?,

    val valkeyUnixSocket: String? = null,
    val valkeyHost: String? = null,
    val valkeyPort: Int? = 6379,
    val valkeyRetention: String? = "7d",

    val valkeyAuthUsername: String? = null,
    val valkeyAuthPassword: String? = null,
) {
    val configuredSigningKey by lazy { signingKey?.let { KeyManager.resolveSerializedKeyBlocking(it.toString()) } }
    val configuredVerificationKey by lazy { KeyManager.resolveSerializedKeyBlocking(verificationKey.toString()) }

    @Transient
    val flowConfigs = ArrayList<AuthFlow>().apply {
        require(authFlow != null || authFlows != null) {
            "No auth flow was defined"
        }
        if (authFlow != null)
            add(authFlow)
        if (authFlows != null)
            addAll(authFlows)
    }

    enum class AuthnzTokens {
        JWT,
        STORE_IN_MEMORY,
        STORE_VALKEY
    }
}

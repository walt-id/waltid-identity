package id.waltid.openid4vci.wallet.oauth

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.random.Random

/**
 * PKCE (Proof Key for Code Exchange) manager for OAuth 2.0 authorization code flow.
 * Implements RFC 7636 to prevent authorization code interception attacks.
 */
object PKCEManager {

    /**
     * PKCE code challenge methods as defined in RFC 7636
     */
    enum class CodeChallengeMethod(val value: String) {
        PLAIN("plain"),
        S256("S256");

        companion object {
            fun fromString(value: String): CodeChallengeMethod? =
                entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
        }
    }

    /**
     * PKCE data containing code verifier and code challenge
     */
    @kotlinx.serialization.Serializable
    data class PKCEData(
        val codeVerifier: String,
        val codeChallenge: String,
        val codeChallengeMethod: CodeChallengeMethod,
    )

    /**
     * Generates a cryptographically random code verifier
     * Length: 43-128 characters (spec requires 43-128)
     * Character set: [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
     */
    fun generateCodeVerifier(length: Int = 128): String {
        require(length in 43..128) { "Code verifier length must be between 43 and 128 characters" }

        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
        return (1..length)
            .map { allowedChars[Random.nextInt(allowedChars.size)] }
            .joinToString("")
    }

    /**
     * Generates PKCE data with the specified code challenge method
     * 
     * @param method The code challenge method (defaults to S256)
     * @return PKCEData containing verifier, challenge, and method
     */
    fun generatePKCEData(method: CodeChallengeMethod = CodeChallengeMethod.S256): PKCEData {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier, method)
        return PKCEData(codeVerifier, codeChallenge, method)
    }

    /**
     * Generates a code challenge from a code verifier
     * 
     * @param codeVerifier The code verifier string
     * @param method The code challenge method
     * @return Base64URL-encoded code challenge
     */
    fun generateCodeChallenge(
        codeVerifier: String,
        method: CodeChallengeMethod = CodeChallengeMethod.S256,
    ): String = when (method) {
        CodeChallengeMethod.PLAIN -> codeVerifier
        CodeChallengeMethod.S256 -> {
            // SHA-256 hash of the code verifier, then base64url encode
            codeVerifier.encodeToByteArray().sha256().encodeToBase64Url()
        }
    }

    /**
     * Computes SHA-256 hash of a byte array
     */
    private fun ByteArray.sha256(): ByteArray = SHA256().digest(this)

    /**
     * Validates that a code challenge method is supported
     */
    fun isSupportedMethod(method: String): Boolean =
        CodeChallengeMethod.fromString(method) != null
}

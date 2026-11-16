package id.walt.openid4vci.pkce

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.random.CryptoRand

/** Platform-neutral PKCE primitives shared by authorization servers and wallets. */
object Pkce {
    /** Generates at least 256 bits of entropy, including at the minimum supported length. */
    fun generateCodeVerifier(length: Int = 128): String {
        require(length in 43..128) { "Code verifier length must be between 43 and 128 characters" }
        return CryptoRand.nextBytes(ByteArray((length * 3 + 3) / 4)).encodeToBase64Url().take(length)
    }

    /** The caller validates verifier syntax when accepting protocol input. */
    fun generateS256CodeChallenge(codeVerifier: String): String =
        SHA256().digest(codeVerifier.encodeToByteArray()).encodeToBase64Url()

    internal fun isValidVerifier(value: String): Boolean =
        value.length in 43..128 && value.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-._~"
        }
}

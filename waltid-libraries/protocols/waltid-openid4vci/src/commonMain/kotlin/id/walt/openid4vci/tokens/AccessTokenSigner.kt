package id.walt.openid4vci.tokens

import id.walt.crypto.keys.Key

/**
 * Resolves the signing key. callback pattern so the library stays
 * agnostic of key storage (local, KMS, etc.). The application supplies the resolver when
 * constructing the TokenService.
 */
fun interface SigningKeyResolver {
    suspend fun resolveSigningKey(): Key
}

/**
 * Access-token signer contract. Returns both the compact
 * JWT and its signature fragment so callers can persist either form.
 */
interface AccessTokenSigner {
    suspend fun sign(
        claims: Map<String, Any?>,
        header: Map<String, Any?> = emptyMap(),
    ): SignedAccessToken
}

data class SignedAccessToken(
    val token: String,
    val signature: String,
)

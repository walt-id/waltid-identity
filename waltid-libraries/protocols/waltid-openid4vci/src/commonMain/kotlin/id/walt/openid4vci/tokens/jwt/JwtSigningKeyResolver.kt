package id.walt.openid4vci.tokens.jwt

import id.walt.crypto.keys.Key

/**
 * Resolves the signing key for JWT access tokens. Kept in the JWT module so opaque tokens
 * don't need to think about signing at all.
 */
fun interface JwtSigningKeyResolver {
    suspend fun resolveSigningKey(): Key
}

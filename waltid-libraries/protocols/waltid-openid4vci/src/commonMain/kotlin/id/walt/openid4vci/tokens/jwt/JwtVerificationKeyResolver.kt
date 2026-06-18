package id.walt.openid4vci.tokens.jwt

import id.walt.crypto.keys.Key
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the verification key for JWT token implementations.
 * Implementations can use header data (e.g., kid) to pick the right key.
 */
fun interface JwtVerificationKeyResolver {
    suspend fun resolveVerificationKey(header: JsonObject): Key
}

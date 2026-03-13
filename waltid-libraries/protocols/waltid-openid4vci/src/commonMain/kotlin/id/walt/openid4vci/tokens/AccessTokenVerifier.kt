package id.walt.openid4vci.tokens

import kotlinx.serialization.json.JsonObject

/**
 * Minimal contract for verifying access tokens.
 * Implementations may verify JWTs locally or call an introspection endpoint.
 */
interface AccessTokenVerifier {
    suspend fun verify(
        token: String,
        expectedIssuer: String? = null,
        expectedAudience: String? = null,
    ): JsonObject
}

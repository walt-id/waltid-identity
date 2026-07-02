package id.walt.openid4vci.tokens.jwt

import id.walt.crypto.utils.JwsUtils.decodeJws
import kotlinx.serialization.json.JsonObject

internal class JwtTokenVerifier(
    private val resolver: JwtVerificationKeyResolver,
) {
    suspend fun verify(token: String, tokenName: String): JsonObject {
        val decoded = token.decodeJws()
        val verificationKey = resolver.resolveVerificationKey(decoded.header)
        val verifiedPayload = verificationKey.verifyJws(token).getOrElse { cause ->
            throw IllegalArgumentException("Invalid ${tokenName.lowercase()} signature", cause)
        }

        return verifiedPayload as? JsonObject
            ?: throw IllegalArgumentException("$tokenName payload must be a JSON object")
    }
}

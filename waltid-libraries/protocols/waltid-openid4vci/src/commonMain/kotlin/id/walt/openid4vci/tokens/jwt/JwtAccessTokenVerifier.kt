package id.walt.openid4vci.tokens.jwt

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vci.tokens.AccessTokenVerifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * JWT access-token verifier. Validates signature and optional standard claims.
 */
class JwtAccessTokenVerifier(
    private val resolver: JwtVerificationKeyResolver,
) : AccessTokenVerifier {

    override suspend fun verify(
        token: String,
        expectedIssuer: String?,
        expectedAudience: String?,
    ): JsonObject {
        require(!expectedIssuer.isNullOrBlank()) { "expectedIssuer is required for access token verification" }
        val decoded = token.decodeJws()
        val verificationKey = resolver.resolveVerificationKey(decoded.header)

        val verifiedPayload = verificationKey.verifyJws(token).getOrElse { cause ->
            throw IllegalArgumentException("Invalid access token signature", cause)
        }

        val payload = verifiedPayload as? JsonObject
            ?: throw IllegalArgumentException("Access token payload must be a JSON object")

        val issuer = payload[JwtPayloadClaims.ISSUER]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Access token is missing issuer claim")
        require(issuer == expectedIssuer) {
            "Access token issuer mismatch (expected=$expectedIssuer, got=$issuer)"
        }

        val subject = payload[JwtPayloadClaims.SUBJECT]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Access token is missing subject claim")
        require(subject.isNotBlank()) { "Access token subject claim must not be blank" }

        if (expectedAudience != null) {
            val audiences = payload.extractAudience()
            require(expectedAudience in audiences) {
                "Access token audience mismatch (expected=$expectedAudience, got=${audiences.joinToString()})"
            }
        }

        val exp = payload[JwtPayloadClaims.EXPIRATION]?.jsonPrimitive?.longOrNull
            ?: throw IllegalArgumentException("Access token is missing expiration claim")
        val now = Clock.System.now().epochSeconds
        require(now < exp) { "Access token expired" }

        return payload
    }

    private fun JsonObject.extractAudience(): Set<String> {
        val element = this[JwtPayloadClaims.AUDIENCE] ?: return emptySet()
        return when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
            is JsonPrimitive -> setOf(element.content)
            else -> emptySet()
        }
    }
}

package id.walt.openid4vci.tokens.jwt.refresh

import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.openid4vci.tokens.jwt.JwtTokenVerifier
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import id.walt.openid4vci.tokens.refresh.RefreshTokenClaims
import id.walt.openid4vci.tokens.refresh.RefreshTokenVerifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

class JwtRefreshTokenVerifier(
    verificationKeyResolver: JwtVerificationKeyResolver,
) : RefreshTokenVerifier {
    private val verifier = JwtTokenVerifier(verificationKeyResolver)

    override suspend fun verify(
        token: String,
        expectedIssuer: String?,
        expectedClientId: String,
    ): RefreshTokenClaims {
        require(expectedClientId.isNotBlank()) { "expectedClientId is required for refresh token verification" }

        val payload = verifier.verify(token, "Refresh token")

        val type = payload.stringClaim(JwtPayloadClaims.TYPE)
        require(type == KEYCLOAK_REFRESH_TOKEN_TYPE) {
            "Refresh token type mismatch (expected=$KEYCLOAK_REFRESH_TOKEN_TYPE, got=$type)"
        }

        val issuer = payload.stringClaim(JwtPayloadClaims.ISSUER)
        if (!expectedIssuer.isNullOrBlank()) {
            require(issuer == expectedIssuer) {
                "Refresh token issuer mismatch (expected=$expectedIssuer, got=$issuer)"
            }
        }

        val issuedFor = payload.stringClaim(JwtPayloadClaims.AUTHORIZED_PARTY)
        require(issuedFor == expectedClientId) {
            "Refresh token client mismatch (expected=$expectedClientId, got=$issuedFor)"
        }

        val expiresAt = Instant.fromEpochSeconds(payload.longClaim(JwtPayloadClaims.EXPIRATION))
        require(Clock.System.now() < expiresAt) { "Refresh token expired" }

        val audience = payload.extractAudience()
        if (!expectedIssuer.isNullOrBlank() && audience.isNotEmpty()) {
            require(expectedIssuer in audience) {
                "Refresh token audience mismatch (expected=$expectedIssuer, got=${audience.joinToString()})"
            }
        }

        return RefreshTokenClaims(
            id = payload.stringClaim(JwtPayloadClaims.JWT_ID),
            issuer = issuer,
            subject = payload.stringClaim(JwtPayloadClaims.SUBJECT),
            type = type,
            issuedFor = issuedFor,
            audience = audience,
            scopes = payload[JwtPayloadClaims.SCOPE]?.jsonPrimitive?.contentOrNull
                ?.splitToSequence(' ')
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty(),
            sessionId = payload[JwtPayloadClaims.SESSION_ID]?.jsonPrimitive?.contentOrNull,
            issuedAt = Instant.fromEpochSeconds(payload.longClaim(JwtPayloadClaims.ISSUED_AT)),
            expiresAt = expiresAt,
        )
    }

    private fun JsonObject.stringClaim(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Refresh token is missing $name claim")

    private fun JsonObject.longClaim(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull
            ?: throw IllegalArgumentException("Refresh token is missing $name claim")

    private fun JsonObject.extractAudience(): Set<String> {
        val element = this[JwtPayloadClaims.AUDIENCE] ?: return emptySet()
        return when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
            is JsonPrimitive -> element.contentOrNull?.let(::setOf).orEmpty()
            else -> emptySet()
        }
    }
}

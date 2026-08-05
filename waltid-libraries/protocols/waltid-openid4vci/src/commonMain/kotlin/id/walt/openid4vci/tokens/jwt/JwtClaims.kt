package id.walt.openid4vci.tokens.jwt

import id.walt.openid4vci.DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Helper to build a JWT access token claim set with common fields.
 */
fun defaultAccessTokenClaims(
    subject: String,
    issuer: String,
    audience: String? = null,
    scopes: Set<String> = emptySet(),
    issuedAt: Instant = Clock.System.now(),
    expiresAt: Instant = issuedAt + DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS.seconds,
    additional: Map<String, Any?> = emptyMap(),
): Map<String, Any?> {
    val reserved = setOf(
        JwtPayloadClaims.SUBJECT,
        JwtPayloadClaims.ISSUER,
        JwtPayloadClaims.AUDIENCE,
        JwtPayloadClaims.ISSUED_AT,
        JwtPayloadClaims.EXPIRATION,
        JwtPayloadClaims.SCOPE,
    )

    return buildMap {
        put(JwtPayloadClaims.SUBJECT, subject)
        put(JwtPayloadClaims.ISSUER, issuer)
        audience?.let { put(JwtPayloadClaims.AUDIENCE, it) }
        put(JwtPayloadClaims.ISSUED_AT, issuedAt.epochSeconds)
        put(JwtPayloadClaims.EXPIRATION, expiresAt.epochSeconds)
        if (scopes.isNotEmpty()) put(JwtPayloadClaims.SCOPE, scopes.joinToString(" "))
        additional.forEach { (k, v) ->
            if (k !in reserved) put(k, v)
        }
    }
}

/**
 * Common JWT claim names used by token implementations.
 * Common JWT claim names.
 */
object JwtPayloadClaims {
    const val SUBJECT = "sub"
    const val ISSUER = "iss"
    const val AUDIENCE = "aud"
    const val ISSUED_AT = "iat"
    const val EXPIRATION = "exp"
    const val SCOPE = "scope"
    const val CLIENT_ID = "client_id"
    const val JWT_ID = "jti"
    const val CONFIRMATION = "cnf"
    const val CHALLENGE = "challenge"
    const val TYPE = "typ"
    const val AUTHORIZED_PARTY = "azp"
    const val SESSION_ID = "sid"
    const val PRE_AUTHORIZED_CODE = "pre_authorized_code"
}

/**
 * Standard JWT header parameter names.
 */
object JwtHeaderParams {
    const val TYPE = "typ"
    const val ALGORITHM = "alg"
    const val KEY_ID = "kid"
    const val JSON_WEB_KEY = "jwk"
}

/**
 * Standard JWT confirmation claim member names.
 */
object JwtConfirmationClaims {
    const val JWK = "jwk"
    const val JWK_THUMBPRINT = "jkt"
}

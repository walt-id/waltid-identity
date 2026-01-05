@file:OptIn(kotlin.time.ExperimentalTime::class)

package id.walt.openid4vci.tokens

import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * JWT-based token service. Builds a compact JWS using the resolved key, deriving alg/kid from the
 * key itself. Claims are provided by callers, standard claims can be composed via helpers.
 *
 * callers can swap in a different signer
 * (e.g., HSM-backed) without changing handler code, while the default delegates to a key-resolving
 * JWT signer.
 */
open class TokenService(
    private val signer: AccessTokenSigner,
) {

    constructor(
        resolver: SigningKeyResolver,
        json: Json = Json { encodeDefaults = true },
    ) : this(DefaultJwtAccessTokenSigner(resolver, json))

    /**
     * Create a JWT access token using the provided claims and return both token and signature.
     */
    open suspend fun createSignedAccessToken(
        claims: Map<String, Any?>,
        header: Map<String, Any?> = emptyMap(),
    ): SignedAccessToken = signer.sign(claims, header)

    /**
     * Convenience wrapper returning only the compact JWT.
     */
    open suspend fun createAccessToken(claims: Map<String, Any?>): String =
        createSignedAccessToken(claims).token

}

/**
 * Build a simple JWT claim set with common fields.
 */
fun defaultAccessTokenClaims(
    subject: String,
    issuer: String,
    audience: String? = null,
    scopes: Set<String> = emptySet(),
    issuedAt: Instant = Clock.System.now(),
    expiresAt: Instant = issuedAt,
    additional: Map<String, Any?> = emptyMap(),
): Map<String, Any?> = buildMap {
    put("sub", subject)
    put("iss", issuer)
    audience?.let { put("aud", it) }
    put("iat", issuedAt.epochSeconds)
    put("exp", expiresAt.epochSeconds)
    if (scopes.isNotEmpty()) put("scope", scopes.joinToString(" "))
    putAll(additional)
}

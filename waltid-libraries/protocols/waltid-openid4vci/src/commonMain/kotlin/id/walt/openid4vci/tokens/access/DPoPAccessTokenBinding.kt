package id.walt.openid4vci.tokens.access

import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.core.TOKEN_TYPE_DPOP
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.tokens.jwt.JwtConfirmationClaims
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun AccessTokenRequest.dpopAccessTokenClaims(): Map<String, Any?> =
    dpopJwkThumbprint?.let { thumbprint ->
        mapOf(
            JwtPayloadClaims.CONFIRMATION to mapOf(
                JwtConfirmationClaims.JWK_THUMBPRINT to thumbprint,
            ),
        )
    }.orEmpty()

internal fun AccessTokenRequest.accessTokenType(): String =
    if (dpopJwkThumbprint == null) TOKEN_TYPE_BEARER else TOKEN_TYPE_DPOP

internal fun JsonObject.dpopJwkThumbprint(): String? {
    val confirmation = this[JwtPayloadClaims.CONFIRMATION] ?: return null
    require(confirmation is JsonObject) { "Access token has an invalid confirmation claim" }

    val thumbprint = confirmation[JwtConfirmationClaims.JWK_THUMBPRINT] ?: return null
    return (thumbprint as? JsonPrimitive)
        ?.takeIf { it.isString && it.content.isNotBlank() }
        ?.content
        ?: throw IllegalArgumentException("Access token has an invalid DPoP key binding")
}

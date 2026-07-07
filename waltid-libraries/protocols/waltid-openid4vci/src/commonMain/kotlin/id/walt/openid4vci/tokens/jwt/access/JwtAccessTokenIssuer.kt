package id.walt.openid4vci.tokens.jwt.access

import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtTokenSigner
import id.walt.openid4vci.tokens.jwt.compactJwsSignature
import kotlinx.serialization.json.Json

/**
 * Default JWT-based access token issuer (RFC 9068). Uses a signer resolved via callback.
 */
class JwtAccessTokenIssuer internal constructor(
    private val signer: JwtTokenSigner,
) : AccessTokenIssuer {

    constructor(
        resolver: JwtSigningKeyResolver,
        json: Json = Json { encodeDefaults = true },
    ) : this(
        signer =
            JwtTokenSigner(
                resolver = resolver,
                json = json
            )
    )

    override suspend fun issue(claims: Map<String, Any?>): String =
        signer.sign(claims)

    override fun signature(token: String): String =
        compactJwsSignature(token, "Access token")
}
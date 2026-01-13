package id.walt.openid4vci.tokens.jwt

import id.walt.openid4vci.tokens.AccessTokenService
import kotlinx.serialization.json.Json

/**
 * Default JWT-based access token service (RFC 9068). Uses a signer resolved via callback.
 */
class JwtAccessTokenService(
    private val signer: JwtAccessTokenSigner,
) : AccessTokenService {

    constructor(
        resolver: JwtSigningKeyResolver,
        json: Json = Json { encodeDefaults = true },
    ) : this(
        signer =
            DefaultJwtAccessTokenSigner(
                resolver = resolver,
                json = json
            )
    )

    override suspend fun createAccessToken(claims: Map<String, Any?>): String =
        signer.sign(claims)
}

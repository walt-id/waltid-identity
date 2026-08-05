package id.walt.openid4vci.tokens.jwt.access

import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKey
import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtTokenSigner
import id.walt.openid4vci.tokens.jwt.compactJwsSignature
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import kotlinx.serialization.json.Json

/**
 * Default JWT-based access token issuer (RFC 9068). Uses a signer resolved via callback.
 */
class JwtAccessTokenIssuer internal constructor(
    private val signer: JwtTokenSigner,
) : AccessTokenIssuer {

    @Deprecated("Use the Crypto2Key constructor or crypto2 resolver factory")
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

    constructor(
        signingKey: Key,
        algorithm: JwsAlgorithm,
        keyId: String = signingKey.id.value,
        json: Json = Json { encodeDefaults = true },
    ) : this(JwtTokenSigner(Crypto2JwtSigningKeyResolver {
        Crypto2JwtSigningKey(signingKey, algorithm, keyId)
    }, json))

    override suspend fun issue(claims: Map<String, Any?>): String =
        signer.sign(claims)

    override fun signature(token: String): String =
        compactJwsSignature(token, "Access token")

    companion object {
        fun crypto2(
            resolver: Crypto2JwtSigningKeyResolver,
            json: Json = Json { encodeDefaults = true },
        ): JwtAccessTokenIssuer = JwtAccessTokenIssuer(JwtTokenSigner(resolver, json))
    }
}

package id.walt.openid4vci.tokens.jwt.refresh

import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtTokenSigner
import id.walt.openid4vci.tokens.jwt.compactJwsSignature
import id.walt.openid4vci.tokens.refresh.RefreshTokenGenerationRequest
import id.walt.openid4vci.tokens.refresh.RefreshTokenIssuer
import org.kotlincrypto.random.CryptoRand
import kotlin.io.encoding.Base64

const val KEYCLOAK_REFRESH_TOKEN_TYPE = "Refresh"

class JwtRefreshTokenIssuer internal constructor(
    private val signer: JwtTokenSigner,
) : RefreshTokenIssuer {

    constructor(
        signingKeyResolver: JwtSigningKeyResolver,
    ) : this(
        signer = JwtTokenSigner(signingKeyResolver),
    )

    override suspend fun issue(request: RefreshTokenGenerationRequest): String {
        require(request.issuer.isNotBlank()) { "issuer is required for refresh token generation" }
        require(request.subject.isNotBlank()) { "subject is required for refresh token generation" }

        val claims = buildMap<String, Any?> {
            put(JwtPayloadClaims.JWT_ID, generateTokenId())
            put(JwtPayloadClaims.ISSUER, request.issuer)
            put(JwtPayloadClaims.SUBJECT, request.subject)
            put(JwtPayloadClaims.AUDIENCE, request.issuer)
            put(JwtPayloadClaims.ISSUED_AT, request.issuedAt.epochSeconds)
            put(JwtPayloadClaims.EXPIRATION, request.expiresAt.epochSeconds)
            put(JwtPayloadClaims.TYPE, KEYCLOAK_REFRESH_TOKEN_TYPE)
            request.clientId?.takeIf { it.isNotBlank() }?.let {
                put(JwtPayloadClaims.AUTHORIZED_PARTY, it)
            }
            request.sessionId?.takeIf { it.isNotBlank() }?.let { put(JwtPayloadClaims.SESSION_ID, it) }
            request.scopes.takeIf { it.isNotEmpty() }?.let { put(JwtPayloadClaims.SCOPE, it.joinToString(" ")) }
        }

        return signer.sign(claims)
    }

    override fun signature(token: String): String =
        compactJwsSignature(token, "Refresh token")

    private fun generateTokenId(): String =
        Base64.UrlSafe.encode(CryptoRand.nextBytes(ByteArray(TOKEN_ID_BYTES)))

    private companion object {
        const val TOKEN_ID_BYTES = 32
    }
}

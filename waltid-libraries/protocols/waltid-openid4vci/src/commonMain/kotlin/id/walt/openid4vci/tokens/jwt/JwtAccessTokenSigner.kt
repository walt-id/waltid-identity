package id.walt.openid4vci.tokens.jwt

/**
 * JWT access-token signer contract. Only used by signed formats.
 */
interface JwtAccessTokenSigner {
    suspend fun sign(
        claims: Map<String, Any?>,
        header: Map<String, Any?> = emptyMap(),
    ): String
}

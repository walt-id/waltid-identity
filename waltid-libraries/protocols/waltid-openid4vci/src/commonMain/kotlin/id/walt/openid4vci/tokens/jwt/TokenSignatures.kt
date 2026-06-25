package id.walt.openid4vci.tokens.jwt

internal fun compactJwsSignature(token: String, tokenName: String): String {
    val parts = token.split('.')
    require(parts.size == 3 && parts[2].isNotBlank()) { "$tokenName must be a compact JWS" }
    return parts[2]
}

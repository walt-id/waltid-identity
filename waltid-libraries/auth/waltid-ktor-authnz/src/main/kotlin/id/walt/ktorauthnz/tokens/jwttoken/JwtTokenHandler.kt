package id.walt.ktorauthnz.tokens.jwttoken

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.tokens.TokenHandler
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class JwtTokenHandler : TokenHandler {

    lateinit var signingKey: Key
    lateinit var verificationKey: Key

    override suspend fun generateToken(session: AuthSession): String {
        val payload = buildJsonObject {
            put("sub", session.accountId)
            put("session", session.id)
        }.toString().toByteArray()

        return signingKey.signJws(payload)
    }

    override suspend fun validateToken(token: String): Boolean {
        return verificationKey.verifyJws(token).isSuccess
    }

    private fun String.getTokenClaim(claim: String) =
        decodeJws().payload[claim]?.jsonPrimitive?.content ?: error("no \"$claim\" in token")

    override suspend fun getTokenSessionId(token: String): String {
        return token.getTokenClaim("session")
    }

    override suspend fun getTokenAccountId(token: String): String {
        return token.getTokenClaim("sub")
    }

    override suspend fun dropToken(token: String) {
        // No operation (is JWT)
    }
}

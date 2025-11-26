@file:OptIn(ExperimentalTime::class)

package id.walt.ktorauthnz.tokens.jwttoken

import id.walt.commons.web.ExpiredTokenException
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.tokens.TokenHandler
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class JwtTokenHandler : TokenHandler {

    override val name = "token-jwt"

    lateinit var signingKey: Key
    lateinit var verificationKey: Key

    override suspend fun generateToken(session: AuthSession): String {
        val payload = buildJsonObject {
            put("sub", session.accountId)
            put("session", session.id)
            if (session.expiration != null) put("exp", session.expiration!!.epochSeconds)
        }.toString().toByteArray()

        return signingKey.signJws(payload)
    }

    /** Check JWT `exp` if in token */
    fun checkExpirationIfExists(jwtPayload: JsonObject) {
        jwtPayload["exp"]?.jsonPrimitive?.long?.let { exp ->
            val expirationDate = Instant.fromEpochSeconds(exp)
            val now = Clock.System.now()
            authCheck(now < expirationDate ,
                ExpiredTokenException("JWT Login Token expired since: ${now - expirationDate}")
            )
        }
    }

    override suspend fun validateToken(token: String): Boolean {
        checkExpirationIfExists(token.decodeJws().payload)

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

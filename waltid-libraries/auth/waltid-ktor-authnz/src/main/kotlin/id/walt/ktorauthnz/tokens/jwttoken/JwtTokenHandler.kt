package id.walt.ktorauthnz.tokens.jwttoken

import id.walt.commons.web.ExpiredTokenException
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.tokens.TokenHandler
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant

class JwtTokenHandler private constructor(
    var crypto2Keys: Crypto2JwtTokenKeys?,
) : TokenHandler {

    @Deprecated("Use the Crypto2Key constructor")
    constructor() : this(null)

    constructor(
        signingKey: Crypto2Key,
        verificationKey: Crypto2Key = signingKey,
        algorithm: JwsAlgorithm,
        keyId: String = signingKey.id.value,
    ) : this(Crypto2JwtTokenKeys(signingKey, verificationKey, algorithm, keyId))

    override val name = "token-jwt"

    @Deprecated("Use the Crypto2Key constructor")
    lateinit var signingKey: Key

    @Deprecated("Use the Crypto2Key constructor")
    lateinit var verificationKey: Key
    var clock: Clock = Clock.System

    override suspend fun generateToken(session: AuthSession): String {
        val payload = buildJsonObject {
            put("sub", session.accountId)
            put("session", session.id)
            if (session.expiration != null) put("exp", session.expiration!!.epochSeconds)
        }.toString().toByteArray()

        crypto2Keys?.let { keys ->
            return CompactJws.sign(
                payload = payload,
                key = keys.signingKey,
                algorithm = keys.algorithm,
                protectedHeader = buildJsonObject {
                    put("typ", "JWT")
                    put("kid", keys.keyId)
                },
            )
        }
        return signingKey.signJws(payload)
    }

    /** Check JWT `exp` if in token */
    fun checkExpirationIfExists(jwtPayload: JsonObject) {
        jwtPayload["exp"]?.jsonPrimitive?.long?.let { exp ->
            val expirationDate = Instant.fromEpochSeconds(exp)
            val now = clock.now()
            authCheck(
                now < expirationDate,
                ExpiredTokenException("JWT Login Token expired since: ${now - expirationDate}")
            )
        }
    }

    override suspend fun validateToken(token: String): Boolean {
        crypto2Keys?.let { keys ->
            val verified = try {
                CompactJws.verify(token, keys.verificationKey, keys.algorithm)
            } catch (_: IllegalArgumentException) {
                return false
            }
            val payload = Json.parseToJsonElement(verified.payload.decodeToString()) as? JsonObject
                ?: return false
            checkExpirationIfExists(payload)
            return true
        }
        checkExpirationIfExists(token.decodeJws().payload)

        return verificationKey.verifyJws(token).isSuccess
    }

    private fun String.getTokenClaim(claim: String): String {
        val payload = if (crypto2Keys != null) {
            val decoded = CompactJws.decodeUnverified(this)
            Json.parseToJsonElement(decoded.payload.decodeToString()) as? JsonObject
                ?: error("JWT payload is not a JSON object")
        } else {
            decodeJws().payload
        }
        return payload[claim]?.jsonPrimitive?.content ?: error("no \"$claim\" in token")
    }

    override suspend fun getTokenSessionId(token: String): String {
        return token.getTokenClaim("session")
    }

    override suspend fun getTokenAccountId(token: String): String {
        return token.getTokenClaim("sub")
    }

    override suspend fun dropToken(token: String) {
        // No operation (is JWT)
    }

    companion object {
        fun crypto2(
            signingKey: Crypto2Key,
            verificationKey: Crypto2Key = signingKey,
            algorithm: JwsAlgorithm,
            keyId: String = signingKey.id.value,
        ): JwtTokenHandler = JwtTokenHandler(signingKey, verificationKey, algorithm, keyId)
    }
}

data class Crypto2JwtTokenKeys(
    val signingKey: Crypto2Key,
    val verificationKey: Crypto2Key,
    val algorithm: JwsAlgorithm,
    val keyId: String,
) {
    init {
        require(keyId.isNotBlank()) { "JWT token key ID cannot be blank" }
        require(signingKey.capabilities.signer != null) { "JWT token signing key does not permit signing" }
        require(verificationKey.capabilities.verifier != null) { "JWT token verification key does not permit verification" }
        require(signingKey.capabilities.supportsSignatureAlgorithm(algorithm.toSignatureAlgorithm())) {
            "JWT token signing key does not support ${algorithm.identifier}"
        }
        require(verificationKey.capabilities.supportsSignatureAlgorithm(algorithm.toSignatureAlgorithm())) {
            "JWT token verification key does not support ${algorithm.identifier}"
        }
    }
}

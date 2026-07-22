package id.walt.openid4vci.tokens.jwt.access

import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.tokens.jwt.Crypto2JwtVerificationKey
import id.walt.openid4vci.tokens.jwt.Crypto2JwtVerificationKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.openid4vci.tokens.jwt.JwtTokenVerifier
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import id.walt.openid4vci.tokens.access.AccessTokenVerifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * JWT access-token verifier. Validates signature and optional standard claims.
 */
class JwtAccessTokenVerifier private constructor(
    private val verifier: JwtTokenVerifier,
) : AccessTokenVerifier {

    @Deprecated("Use the Crypto2Key constructor or crypto2 resolver factory")
    constructor(resolver: JwtVerificationKeyResolver) : this(JwtTokenVerifier(resolver))

    constructor(verificationKey: Key, allowedAlgorithms: Set<JwsAlgorithm>) : this(
        JwtTokenVerifier(Crypto2JwtVerificationKeyResolver {
            Crypto2JwtVerificationKey(verificationKey, allowedAlgorithms)
        })
    )

    override suspend fun verify(
        token: String,
        expectedIssuer: String?,
        expectedAudience: String?,
    ): JsonObject {
        require(!expectedIssuer.isNullOrBlank()) { "expectedIssuer is required for access token verification" }
        val payload = verifier.verify(token, "Access token")

        val issuer = payload[JwtPayloadClaims.ISSUER]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Access token is missing issuer claim")
        require(issuer == expectedIssuer) {
            "Access token issuer mismatch (expected=$expectedIssuer, got=$issuer)"
        }

        val subject = payload[JwtPayloadClaims.SUBJECT]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Access token is missing subject claim")
        require(subject.isNotBlank()) { "Access token subject claim must not be blank" }

        if (expectedAudience != null) {
            val audiences = payload.extractAudience()
            require(expectedAudience in audiences) {
                "Access token audience mismatch (expected=$expectedAudience, got=${audiences.joinToString()})"
            }
        }

        val exp = payload[JwtPayloadClaims.EXPIRATION]?.jsonPrimitive?.longOrNull
            ?: throw IllegalArgumentException("Access token is missing expiration claim")
        val now = Clock.System.now().epochSeconds
        require(now < exp) { "Access token expired" }

        return payload
    }

    private fun JsonObject.extractAudience(): Set<String> {
        val element = this[JwtPayloadClaims.AUDIENCE] ?: return emptySet()
        return when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
            is JsonPrimitive -> setOf(element.content)
            else -> emptySet()
        }
    }

    companion object {
        fun crypto2(resolver: Crypto2JwtVerificationKeyResolver): JwtAccessTokenVerifier =
            JwtAccessTokenVerifier(JwtTokenVerifier(resolver))
    }
}

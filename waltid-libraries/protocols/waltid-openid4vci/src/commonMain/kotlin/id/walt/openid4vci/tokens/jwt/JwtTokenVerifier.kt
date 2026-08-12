package id.walt.openid4vci.tokens.jwt

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto.utils.JwsUtils.decodeJws
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class JwtTokenVerifier private constructor(
    private val resolver: JwtVerificationKeyResolver?,
    private val crypto2Resolver: Crypto2JwtVerificationKeyResolver?,
) {
    constructor(resolver: JwtVerificationKeyResolver) : this(resolver, null)
    constructor(crypto2Resolver: Crypto2JwtVerificationKeyResolver) : this(null, crypto2Resolver)

    suspend fun verify(token: String, tokenName: String): JsonObject {
        crypto2Resolver?.let { resolver ->
            val decoded = CompactJws.decodeUnverified(token)
            val resolved = resolver.resolveVerificationKey(decoded.protectedHeader)
            val verified = try {
                CompactJws.verify(token, resolved.key, resolved.allowedAlgorithms)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                throw IllegalArgumentException("Invalid ${tokenName.lowercase()} signature", cause)
            }
            return Json.parseToJsonElement(verified.payload.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
                ?: throw IllegalArgumentException("$tokenName payload must be a JSON object")
        }

        val decoded = token.decodeJws()
        val verificationKey = requireNotNull(resolver).resolveVerificationKey(decoded.header)
        val verifiedPayload = verificationKey.verifyJws(token).getOrElse { cause ->
            throw IllegalArgumentException("Invalid ${tokenName.lowercase()} signature", cause)
        }

        return verifiedPayload as? JsonObject
            ?: throw IllegalArgumentException("$tokenName payload must be a JSON object")
    }
}

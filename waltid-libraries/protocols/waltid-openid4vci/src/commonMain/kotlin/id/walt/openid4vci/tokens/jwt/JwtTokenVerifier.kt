package id.walt.openid4vci.tokens.jwt

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.errors.ClientRequestException
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
            val decoded = decodeClientToken(tokenName) { CompactJws.decodeUnverified(token) }
            // Resolving the key reads issuer configuration (and possibly a KMS): its failures are not the client's.
            val resolved = resolver.resolveVerificationKey(decoded.protectedHeader)
            val verified = try {
                CompactJws.verify(token, resolved.key, resolved.allowedAlgorithms)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                throw ClientRequestException("Invalid ${tokenName.lowercase()} signature", cause)
            }
            return Json.parseToJsonElement(verified.payload.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
                ?: throw ClientRequestException("$tokenName payload must be a JSON object")
        }

        val decoded = decodeClientToken(tokenName) { token.decodeJws() }
        val verificationKey = requireNotNull(resolver).resolveVerificationKey(decoded.header)
        val verifiedPayload = verificationKey.verifyJws(token).getOrElse { cause ->
            throw ClientRequestException("Invalid ${tokenName.lowercase()} signature", cause)
        }

        return verifiedPayload as? JsonObject
            ?: throw ClientRequestException("$tokenName payload must be a JSON object")
    }
}

private inline fun <T> decodeClientToken(tokenName: String, decode: () -> T): T = try {
    decode()
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Exception) {
    throw ClientRequestException("Malformed ${tokenName.lowercase()}", cause)
}

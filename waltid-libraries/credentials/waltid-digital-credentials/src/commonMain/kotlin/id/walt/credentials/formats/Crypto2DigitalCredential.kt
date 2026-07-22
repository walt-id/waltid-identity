package id.walt.credentials.formats

import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Explicit algorithm allowlists for digital-credential signature verification. */
data class Crypto2VerificationAlgorithms(
    val jws: Set<JwsAlgorithm> = emptySet(),
    val cose: Set<Int> = emptySet(),
)

/** Crypto2 key resolution and verification shared by all maintained digital-credential formats. */
interface Crypto2DigitalCredential {
    suspend fun getSignerCrypto2Key(): Key?

    /** Returns a public verification-only holder key, or null when the credential is not holder-bound. */
    suspend fun getHolderCrypto2Key(): Key?

    suspend fun verifyCrypto2(
        publicKey: Key,
        allowedAlgorithms: Crypto2VerificationAlgorithms,
    ): Result<JsonElement>
}

private val holderCrypto2KeyResolver = Crypto2JwtKeyResolver(allowInlineJwk = true)

internal suspend fun JsonObject.resolveHolderCrypto2Key(): Key? {
    val confirmation = this["cnf"] as? JsonObject ?: return null
    val resolved = when {
        "jwk" in confirmation -> {
            val jwk = confirmation["jwk"] as? JsonObject
                ?: throw IllegalArgumentException("Holder confirmation jwk must be a JSON object")
            require(!Jwk.containsPrivateMaterial(jwk)) { "Holder confirmation JWK must be public" }
            holderCrypto2KeyResolver.resolveFromJwt(
                jwtHeader = buildJsonObject { put("jwk", jwk) },
                jwtPayload = JsonObject(emptyMap()),
            )
        }
        "kid" in confirmation -> {
            val keyId = (confirmation["kid"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Holder confirmation kid must be a non-empty string")
            holderCrypto2KeyResolver.resolveFromJwt(
                jwtHeader = buildJsonObject { put("kid", keyId) },
                jwtPayload = buildJsonObject { put("iss", keyId.substringBefore('#')) },
            )
        }
        else -> return null
    } ?: throw IllegalArgumentException("Could not resolve holder confirmation key")

    return resolved.key.also { key ->
        requireNotNull(key.capabilities.verifier) { "Holder confirmation key does not permit verification" }
        require(key.capabilities.signer == null && key.capabilities.privateKeyExporter == null) {
            "Holder confirmation key must be verification-only"
        }
    }
}

internal suspend fun <T> crypto2Result(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Throwable) {
    Result.failure(cause)
}

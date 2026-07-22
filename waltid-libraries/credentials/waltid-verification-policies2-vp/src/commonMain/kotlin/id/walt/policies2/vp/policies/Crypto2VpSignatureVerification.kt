package id.walt.policies2.vp.policies

import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal data class Crypto2VpVerification(
    val key: Key,
    val result: Result<JsonElement>,
    val source: String,
    val signerIdentifier: String?,
    val certificateChain: List<String>,
)

internal suspend fun verifyJwtVpWithCrypto2(jwt: String, payload: JsonObject): Crypto2VpVerification? {
    val decoded = CompactJws.decodeUnverified(jwt)
    if (decoded.algorithm == JwsAlgorithm.ES256K) return null
    val resolved = Crypto2JwtKeyResolver().resolveFromJwt(decoded.protectedHeader, payload)
        ?: throw IllegalArgumentException("Could not resolve VP signer key")
    return Crypto2VpVerification(
        key = resolved.key,
        result = verifyWithCrypto2(jwt, resolved.key, decoded.algorithm).mapCatching { verifiedPayload ->
            require(verifiedPayload == payload) { "Verified VP JWT payload does not match parsed presentation payload" }
            verifiedPayload
        },
        source = resolved.source.name,
        signerIdentifier = resolved.signerIdentifier,
        certificateChain = resolved.certificateChain,
    )
}

internal suspend fun verifyKbJwtWithCrypto2(
    jwt: String,
    holderKey: Key,
    allowedAlgorithms: Set<JwsAlgorithm>,
): Result<JsonElement> = verifyWithCrypto2(jwt, holderKey, allowedAlgorithms)

private suspend fun verifyWithCrypto2(jwt: String, key: Key, algorithm: JwsAlgorithm): Result<JsonElement> = try {
    Result.success(
        Json.parseToJsonElement(CompactJws.verify(jwt, key, algorithm).payload.decodeToString(throwOnInvalidSequence = true))
    )
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Exception) {
    Result.failure(cause)
}

private suspend fun verifyWithCrypto2(
    jwt: String,
    key: Key,
    allowedAlgorithms: Set<JwsAlgorithm>,
): Result<JsonElement> = try {
    Result.success(
        Json.parseToJsonElement(
            CompactJws.verify(jwt, key, allowedAlgorithms).payload.decodeToString(throwOnInvalidSequence = true)
        )
    )
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Exception) {
    Result.failure(cause)
}

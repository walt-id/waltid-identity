package id.walt.openid4vci.tokens.jwt

import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.serialization.json.JsonObject

data class Crypto2JwtSigningKey(
    val key: Key,
    val algorithm: JwsAlgorithm,
    val keyId: String = key.id.value,
) {
    init {
        require(keyId.isNotBlank()) { "JWT signing key ID cannot be blank" }
        require(key.capabilities.signer != null) { "JWT signing key does not permit signing" }
        require(key.capabilities.supportsSignatureAlgorithm(algorithm.toSignatureAlgorithm())) {
            "JWT signing key does not support ${algorithm.identifier}"
        }
    }
}

fun interface Crypto2JwtSigningKeyResolver {
    suspend fun resolveSigningKey(): Crypto2JwtSigningKey
}

data class Crypto2JwtVerificationKey(
    val key: Key,
    val allowedAlgorithms: Set<JwsAlgorithm>,
) {
    init {
        require(allowedAlgorithms.isNotEmpty()) { "At least one JWT verification algorithm is required" }
        require(key.capabilities.verifier != null) { "JWT verification key does not permit verification" }
    }
}

fun interface Crypto2JwtVerificationKeyResolver {
    suspend fun resolveVerificationKey(header: JsonObject): Crypto2JwtVerificationKey
}

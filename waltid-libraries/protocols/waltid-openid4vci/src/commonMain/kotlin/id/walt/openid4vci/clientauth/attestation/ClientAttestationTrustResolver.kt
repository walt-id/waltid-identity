package id.walt.openid4vci.clientauth.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.serialization.json.JsonObject

interface ClientAttestationTrustResolver {
    suspend fun resolveTrustedAttesterKeys(
        header: JsonObject,
        payload: JsonObject,
    ): List<Key>
}

fun interface ClientAttestationKeyReferenceResolver {
    suspend fun resolveTrustedAttesterKeys(
        reference: String,
        header: JsonObject,
        payload: JsonObject,
    ): List<Key>
}

class StaticJwkClientAttestationTrustResolver(
    private val verificationKey: Key,
) : ClientAttestationTrustResolver {
    @Suppress("UNUSED_PARAMETER")
    override suspend fun resolveTrustedAttesterKeys(
        header: JsonObject,
        payload: JsonObject,
    ): List<Key> = listOf(verificationKey)

    companion object {
        suspend fun fromJwk(jwk: JsonObject): StaticJwkClientAttestationTrustResolver =
            StaticJwkClientAttestationTrustResolver(JWKKey.importJWK(jwk.toString()).getOrThrow())
    }
}

class StaticJwkSetClientAttestationTrustResolver(
    private val verificationKeys: List<Key>,
) : ClientAttestationTrustResolver {
    init {
        require(verificationKeys.isNotEmpty()) { "verificationKeys must not be empty" }
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun resolveTrustedAttesterKeys(
        header: JsonObject,
        payload: JsonObject,
    ): List<Key> = verificationKeys
}

class KeyReferenceClientAttestationTrustResolver(
    private val reference: String,
    private val keyReferenceResolver: ClientAttestationKeyReferenceResolver,
) : ClientAttestationTrustResolver {
    init {
        require(reference.isNotBlank()) { "reference must not be blank" }
    }

    override suspend fun resolveTrustedAttesterKeys(
        header: JsonObject,
        payload: JsonObject,
    ): List<Key> = keyReferenceResolver.resolveTrustedAttesterKeys(reference, header, payload)
}

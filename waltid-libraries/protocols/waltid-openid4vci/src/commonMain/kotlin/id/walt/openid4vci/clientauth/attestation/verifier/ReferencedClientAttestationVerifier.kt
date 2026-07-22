@file:Suppress("DEPRECATION")

package id.walt.openid4vci.clientauth.attestation.verifier

import kotlinx.serialization.json.JsonObject

class ReferencedClientAttestationVerifier(
    private val reference: String,
    private val crypto2Resolver: Crypto2ClientAttestationKeyReferenceResolver? = null,
    private val legacyResolver: ClientAttestationKeyReferenceResolver? = null,
) : ClientAttestationVerifier {
    init {
        require(crypto2Resolver != null || legacyResolver != null) {
            "key-reference client attestation verification requires at least one key reference resolver"
        }
    }

    override suspend fun verifyAttestationJwt(
        jwt: String,
        header: JsonObject,
        payload: JsonObject,
    ): ClientAttestationVerificationResult {
        if (crypto2Resolver != null) {
            val crypto2Keys = crypto2Resolver.resolveTrustedAttesterKeys(reference, header, payload)
            return Crypto2KeyBasedClientAttestationVerifier { _, _ -> crypto2Keys }
                .verifyAttestationJwt(jwt, header, payload)
        }
        return KeyBasedClientAttestationVerifier { resolvedHeader, resolvedPayload ->
            requireNotNull(legacyResolver).resolveTrustedAttesterKeys(reference, resolvedHeader, resolvedPayload)
        }.verifyAttestationJwt(jwt, header, payload)
    }
}

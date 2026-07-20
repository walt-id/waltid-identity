package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto.keys.Key
import kotlinx.serialization.json.JsonObject

fun interface ClientAttestationKeyReferenceResolver {
    suspend fun resolveTrustedAttesterKeys(
        reference: String,
        header: JsonObject,
        payload: JsonObject,
    ): List<Key>
}

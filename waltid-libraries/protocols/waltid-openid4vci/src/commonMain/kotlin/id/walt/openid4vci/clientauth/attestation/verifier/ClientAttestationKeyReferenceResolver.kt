package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.Key as Crypto2Key
import kotlinx.serialization.json.JsonObject

@Deprecated(
    message = "Use Crypto2ClientAttestationKeyReferenceResolver",
    replaceWith = ReplaceWith("Crypto2ClientAttestationKeyReferenceResolver"),
)
fun interface ClientAttestationKeyReferenceResolver {
    suspend fun resolveTrustedAttesterKeys(
        reference: String,
        header: JsonObject,
        payload: JsonObject,
    ): List<Key>
}

fun interface Crypto2ClientAttestationKeyReferenceResolver {
    suspend fun resolveTrustedAttesterKeys(
        reference: String,
        header: JsonObject,
        payload: JsonObject,
    ): List<Crypto2Key>
}

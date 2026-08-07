package id.walt.verifier2.handlers.sessioncreation

import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidService
import id.walt.did.dids.document.MultibasePublicKeys
import id.walt.did.utils.KeyMaterial
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import id.walt.crypto2.keys.Key as Crypto2Key

private const val DECENTRALIZED_IDENTIFIER_PREFIX = "decentralized_identifier:"

/**
 * Resolves the standards-level `kid` used by a signed verifier Request Object.
 *
 * OpenID4VP `decentralized_identifier` authentication requires `kid` to identify the
 * particular DID-document verification method whose key signed the Request Object. Internal
 * KMS/Crypto2 key identifiers are therefore not suitable substitutes for DID verification-method
 * IDs. For other Client Identifier Prefixes, the existing key-ID behavior is preserved.
 */
internal suspend fun requestObjectKid(clientId: String?, signingKey: Key): String {
    val did = clientId.decentralizedIdentifierOrNull() ?: return signingKey.getKeyId()
    val publicJwk = signingKey.getPublicKey().exportJWKObject().toEncodedPublicJwk()
    return resolveVerificationMethodId(did, publicJwk)
}

/** Crypto2 equivalent of [requestObjectKid]. */
internal suspend fun requestObjectKid(clientId: String?, signingKey: Crypto2Key): String {
    val did = clientId.decentralizedIdentifierOrNull() ?: return signingKey.id.value
    return resolveVerificationMethodId(did, signingKey.exportPublicJwk())
}

private suspend fun resolveVerificationMethodId(did: String, signingKey: EncodedKey.Jwk): String {
    val signingKeyThumbprint = Jwk.sha256Thumbprint(signingKey)
    val document = DidService.resolve(did).getOrThrow()
    val methods = document["verificationMethod"] as? JsonArray
        ?: throw IllegalArgumentException("DID document has no verification methods: $did")

    val matchingMethodIds = methods.mapNotNull { element ->
        val method = element as? JsonObject ?: return@mapNotNull null
        val methodId = method["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("DID verification method has no ID: $did")
        val publicJwk = method.publicJwkOrNull() ?: return@mapNotNull null
        methodId.takeIf { Jwk.sha256Thumbprint(publicJwk) == signingKeyThumbprint }
    }.distinct()

    return requireSingleVerificationMethod(did, matchingMethodIds)
}

private suspend fun JsonObject.publicJwkOrNull(): EncodedKey.Jwk? =
    (this["publicKeyJwk"] as? JsonObject)?.toEncodedPublicJwk()
        ?: (this["publicKeyMultibase"] as? JsonPrimitive)
            ?.contentOrNull
            ?.let { MultibasePublicKeys.decode(it).jwk }
        ?: try {
            KeyMaterial.get(this).getOrThrow().getPublicKey().exportJWKObject().toEncodedPublicJwk()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            null
        }

private fun JsonObject.toEncodedPublicJwk(): EncodedKey.Jwk = EncodedKey.Jwk(
    data = BinaryData(Json.encodeToString(this).encodeToByteArray()),
    privateMaterial = false,
)

private fun String?.decentralizedIdentifierOrNull(): String? {
    if (this == null || !startsWith(DECENTRALIZED_IDENTIFIER_PREFIX)) return null

    val did = removePrefix(DECENTRALIZED_IDENTIFIER_PREFIX)
    require(did.isNotBlank()) { "decentralized_identifier client_id must contain a DID" }
    require('#' !in did) {
        "decentralized_identifier client_id must contain a DID, not a verification-method DID URL"
    }
    return did
}

private fun requireSingleVerificationMethod(did: String, methodIds: List<String>): String = when (methodIds.size) {
    1 -> methodIds.single()
    0 -> throw IllegalArgumentException(
        "Verifier signing key is not represented by a verificationMethod in DID '$did'"
    )
    else -> throw IllegalArgumentException(
        "Verifier signing key matches multiple verificationMethods in DID '$did': $methodIds"
    )
}

package id.walt.did.dids.resolver

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.document.MultibasePublicKeys
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import id.walt.did.utils.KeyMaterial
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Resolves DID verification methods to crypto2 keys. */
fun interface Crypto2DidKeyResolver {
    suspend fun resolveToKeys(did: String): Set<Key>

    /**
     * Resolves a specific verification method before importing key material.
     *
     * Implementations that cannot inspect the DID document fail closed when a verification
     * relationship is requested. This keeps lambda-based resolvers source-compatible without
     * silently bypassing DID relationship authorization.
     */
    suspend fun resolveToKeys(
        did: String,
        keyId: String?,
        relationship: VerificationRelationshipType?,
    ): Set<Key> {
        require(relationship == null) {
            "DID resolver does not support verification relationship filtering: $relationship"
        }
        val keys = resolveToKeys(did)
        if (keyId == null) return keys
        val candidates = idCandidates(keyId)
        return keys.filterTo(mutableSetOf()) { key -> idCandidates(key.id.value).any(candidates::contains) }
    }
}

class DidDocumentCrypto2KeyResolver(
    private val resolveDocument: suspend (String) -> Result<JsonObject>,
    private val runtime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
) : Crypto2DidKeyResolver {
    constructor(
        resolver: DidResolver,
        runtime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
    ) : this(resolver::resolve, runtime)

    override suspend fun resolveToKeys(did: String): Set<Key> =
        resolveToKeys(did, keyId = null, relationship = null)

    override suspend fun resolveToKeys(
        did: String,
        keyId: String?,
        relationship: VerificationRelationshipType?,
    ): Set<Key> {
        require(relationship != VerificationRelationshipType.KeyAgreement) {
            "DID keyAgreement methods cannot be resolved as signature verification keys"
        }
        val document = resolveDocument(did).getOrElse { cause ->
            throw IllegalArgumentException("Unable to resolve DID document: $did", cause)
        }
        val methods = resolveMethods(document, did, relationship)
        val selectedMethods = keyId?.let { selectMethods(methods, did, it) } ?: methods
        val keys = selectedMethods.mapNotNull { method ->
            restore(method)
        }.toSet()
        val relationshipDescription = relationship?.let { " for relationship $it" }.orEmpty()
        val keyDescription = keyId?.let { " matching key ID $it" }.orEmpty()
        require(keys.isNotEmpty()) {
            "DID document has no supported verification methods$keyDescription$relationshipDescription: $did"
        }
        return keys
    }

    private fun resolveMethods(
        document: JsonObject,
        did: String,
        relationship: VerificationRelationshipType?,
    ): List<JsonObject> {
        val declaredMethods = (document["verificationMethod"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()
        if (relationship == null) {
            if (declaredMethods.isEmpty()) {
                throw IllegalArgumentException("DID document has no verification methods: $did")
            }
            return declaredMethods
        }

        val relationshipMethods = document[relationship.toString()] as? JsonArray
            ?: throw IllegalArgumentException("DID document has no $relationship methods: $did")
        return relationshipMethods.mapNotNull { entry ->
            when (entry) {
                is JsonObject -> entry
                is JsonPrimitive -> entry.takeIf(JsonPrimitive::isString)
                    ?.let { findReferencedMethod(declaredMethods, did, it.content) }
                else -> null
            }
        }
    }

    private fun findReferencedMethod(
        methods: List<JsonObject>,
        did: String,
        reference: String,
    ): JsonObject? {
        val normalizedReference = normalizeDidUrl(did, reference)
        val matches = methods.filter { method ->
            methodId(method)?.let { normalizeDidUrl(did, it) == normalizedReference } == true
        }
        require(matches.size <= 1) { "Multiple DID verification methods match relationship reference: $reference" }
        return matches.singleOrNull()
    }

    private fun selectMethods(methods: List<JsonObject>, did: String, keyId: String): List<JsonObject> {
        val normalizedKeyId = normalizeDidUrl(did, keyId)
        val exactMatches = methods.filter { method ->
            methodId(method)?.let { normalizeDidUrl(did, it) == normalizedKeyId } == true
        }
        require(exactMatches.size <= 1) { "Multiple DID verification methods match key ID: $keyId" }
        if (exactMatches.isNotEmpty()) return exactMatches

        if ('#' in keyId || keyId.startsWith("did:")) return emptyList()
        val fragmentMatches = methods.filter { method ->
            methodId(method)?.substringAfter('#', missingDelimiterValue = "") == keyId
        }
        require(fragmentMatches.size <= 1) { "Multiple DID verification methods match key ID fragment: $keyId" }
        return fragmentMatches
    }

    private suspend fun restore(method: JsonObject): Key? {
        val verificationMethodId = methodId(method) ?: return null
        return try {
            val jwk = (method["publicKeyJwk"] as? JsonObject)
                ?: (method["publicKeyMultibase"] as? JsonPrimitive)?.content?.let { multibase ->
                    // crypto2-native: no v1 key import, which is what fails on JS.
                    Json.parseToJsonElement(
                        MultibasePublicKeys.decode(multibase).jwk.data.toByteArray().decodeToString()
                    ).jsonObject
                }
                ?: run {
                    // Legacy material only (publicKeyBase58/Hex), which carries no multicodec prefix.
                    KeyMaterial.get(method).getOrThrow().getPublicKey().exportJWKObject()
                }
            val stored = EncodedKey.Jwk(
                BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
                privateMaterial = false,
            ).toStoredSoftwareKey(KeyId(verificationMethodId), setOf(KeyUsage.VERIFY))
            runtime.restore(stored)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            null
        }
    }

    private fun methodId(method: JsonObject): String? =
        (method["id"] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)
}

private fun normalizeDidUrl(did: String, id: String): String = if (id.startsWith('#')) "$did$id" else id

private fun idCandidates(id: String): Set<String> =
    setOf(id, id.substringAfter('#', missingDelimiterValue = ""))
        .filter(String::isNotBlank)
        .toSet()

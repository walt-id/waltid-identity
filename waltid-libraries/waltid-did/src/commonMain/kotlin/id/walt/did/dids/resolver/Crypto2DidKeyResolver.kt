package id.walt.did.dids.resolver

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import id.walt.did.dids.document.MultibasePublicKeys
import id.walt.did.utils.KeyMaterial
import id.walt.webdatafetching.removeDotSegments
import io.ktor.http.encodedPath
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
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
}

class DidDocumentCrypto2KeyResolver(
    private val resolver: DidResolver,
    private val runtime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
) : Crypto2DidKeyResolver {
    private var verificationRelationship: VerificationRelationshipType? = null

    constructor(
        resolver: DidResolver,
        runtime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
        verificationRelationship: VerificationRelationshipType?,
    ) : this(resolver, runtime) {
        this.verificationRelationship = verificationRelationship
    }

    private fun normalizeId(id: String, did: String): String {
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(id)) return id
        // DID Core 3.2.2 treats method-name:method-specific-id as the authority for RFC 3986 resolution.
        val baseDid = did.substringBefore('/').substringBefore('?').substringBefore('#')
        if (id.startsWith('#') || id.startsWith('?') || id.isEmpty()) return baseDid + id
        val resolved = URLBuilder("https://did.invalid/").apply {
            takeFrom(id)
            encodedPath = removeDotSegments(encodedPath)
        }.buildString()
        return if (resolved.startsWith("https://did.invalid")) baseDid + resolved.removePrefix("https://did.invalid")
        else "did:" + resolved.substringAfter("://")
    }

    override suspend fun resolveToKeys(did: String): Set<Key> {
        val document = resolver.resolve(did).getOrElse { cause ->
            throw IllegalArgumentException("Unable to resolve DID document: $did", cause)
        }
        val methods = if (verificationRelationship == null) {
            document["verificationMethod"] as? JsonArray
                ?: throw IllegalArgumentException("DID document has no verification methods: $did")
        } else {
            // DID Core permits both embedded methods and references in verification relationships.
            val references = (document["verificationMethod"] as? JsonArray).orEmpty()
                .filterIsInstance<JsonObject>()
                .associateBy { normalizeId(it["id"]?.jsonPrimitive?.content.orEmpty(), did) }
            val relationship = document[verificationRelationship.toString()] as? JsonArray
                ?: throw IllegalArgumentException("DID document has no $verificationRelationship methods: $did")
            JsonArray(relationship.mapNotNull { entry ->
                when (entry) {
                    is JsonObject -> entry
                    is JsonPrimitive -> references[normalizeId(entry.content, did)]
                    else -> null
                }
            })
        }
        val keys = methods.mapNotNull { element ->
            val method = element as? JsonObject ?: return@mapNotNull null
            val jwk = (method["publicKeyJwk"] as? JsonObject)
                ?: (method["publicKeyMultibase"] as? JsonPrimitive)?.content?.let { multibase ->
                    // crypto2-native: no v1 key import, which is what fails on JS.
                    try {
                        Json.parseToJsonElement(
                            MultibasePublicKeys.decode(multibase).jwk.data.toByteArray().decodeToString()
                        ).jsonObject
                    } catch (cause: CancellationException) {
                        throw cause
                    } catch (_: Throwable) {
                        null
                    }
                }
                ?: try {
                    // Legacy material only (publicKeyBase58/Hex), which carries no multicodec prefix.
                    KeyMaterial.get(method).getOrThrow().getPublicKey().exportJWKObject()
                } catch (cause: CancellationException) {
                    throw cause
                } catch (_: Exception) {
                    return@mapNotNull null
                }
            val verificationMethodId = method["id"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("DID verification method has no ID")
            val stored = EncodedKey.Jwk(
                BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
                privateMaterial = false,
            ).toStoredSoftwareKey(KeyId(normalizeId(verificationMethodId, did)), setOf(KeyUsage.VERIFY))
            try {
                runtime.restore(stored)
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
                null
            }
        }.toSet()
        require(keys.isNotEmpty()) { "DID document has no supported verification methods: $did" }
        return keys
    }
}

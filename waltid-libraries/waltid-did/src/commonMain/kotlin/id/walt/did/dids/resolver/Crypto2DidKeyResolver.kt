package id.walt.did.dids.resolver

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.document.MultibasePublicKeys
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
}

class DidDocumentCrypto2KeyResolver(
    private val resolver: DidResolver,
    private val runtime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
) : Crypto2DidKeyResolver {
    override suspend fun resolveToKeys(did: String): Set<Key> {
        val document = resolver.resolve(did).getOrElse { cause ->
            throw IllegalArgumentException("Unable to resolve DID document: $did", cause)
        }
        val methods = document["verificationMethod"] as? JsonArray
            ?: throw IllegalArgumentException("DID document has no verification methods: $did")
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
            ).toStoredSoftwareKey(KeyId(verificationMethodId), setOf(KeyUsage.VERIFY))
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

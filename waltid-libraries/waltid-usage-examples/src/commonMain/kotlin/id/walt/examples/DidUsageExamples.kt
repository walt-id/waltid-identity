package id.walt.examples

import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * How an application creates a DID for a key it holds and how a relying party gets back a usable verification
 * key from nothing but the DID string. Both `did:key` and `did:jwk` are self-contained, so this runs offline on
 * every platform - no resolver endpoint, no network.
 */
object DidUsageExamples {
    /** Registers [method] for [key], resolves the DID again, and checks the resolved key verifies its signatures. */
    suspend fun createResolveAndVerify(method: DidMethod, key: Key): DidExampleResult {
        DidService.minimalInit()

        val registered = Crypto2DidService.registerByKey(method.method, key, method.createOptions())
        val document = Crypto2DidService.resolve(registered.did).getOrThrow()
        val resolvedKeys = Crypto2DidService.resolveToKeys(registered.did).getOrThrow()

        // What a verifier actually does: take the key from the DID document and check a signature with it.
        val message = "credential-bytes".encodeToByteArray()
        val algorithm = ExampleKeys.signingSpecs.first { it.first == key.spec }.second
        val signature = requireNotNull(key.capabilities.signer) { "Example key cannot sign" }
            .sign(message, algorithm.toSignatureAlgorithm())
        val verifiedByResolvedKey = resolvedKeys.any { resolved ->
            resolved.capabilities.verifier?.verify(message, signature, algorithm.toSignatureAlgorithm()) == true
        }

        return DidExampleResult(
            did = registered.did,
            method = method,
            document = document,
            resolvedKeyCount = resolvedKeys.size,
            verifiedByResolvedKey = verifiedByResolvedKey,
        )
    }

    enum class DidMethod(val method: String) {
        KEY("key"),
        JWK("jwk");

        internal fun createOptions() = when (this) {
            KEY -> DidKeyCreateOptions()
            JWK -> DidJwkCreateOptions()
        }
    }

    data class DidExampleResult(
        val did: String,
        val method: DidMethod,
        val document: JsonObject,
        val resolvedKeyCount: Int,
        val verifiedByResolvedKey: Boolean,
    ) {
        /** The identifier a credential's `kid` has to carry so that a verifier can find this key again. */
        val verificationMethodId: String
            get() = document["verificationMethod"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("id")?.jsonPrimitive?.content
                ?: did
    }
}

/** Convenience for the common case: generate a key for [spec] and run the DID round trip with it. */
suspend fun DidUsageExamples.createResolveAndVerify(
    method: DidUsageExamples.DidMethod,
    spec: KeySpec,
): DidUsageExamples.DidExampleResult = createResolveAndVerify(method, ExampleKeys.signingKey(spec))

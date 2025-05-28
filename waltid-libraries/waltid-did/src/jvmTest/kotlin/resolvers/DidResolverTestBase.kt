package resolvers

import DidDocumentChecks.defaultKeyChecks
import DidDocumentChecks.ed25519KeyChecks
import DidDocumentChecks.rsaKeyChecks
import DidDocumentChecks.secp256KeyChecks
import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.resolver.local.LocalResolverMethod
import io.ktor.client.network.sockets.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import resolvers.DidResolverTestBase.Companion.didDocAssertions
import resolvers.DidResolverTestBase.Companion.ed25519DidAssertions
import resolvers.DidResolverTestBase.Companion.ed25519KeyAssertions
import resolvers.DidResolverTestBase.Companion.keyAssertions
import kotlin.test.assertNotNull

abstract class DidResolverTestBase {
    protected abstract val resolver: LocalResolverMethod

    /**
     * Some online resolvers are a bit flaky with their uptime, but we still want to test with them,
     * e.g., to catch if the JSON response changes.
     * However, they should not break our pipeline just because their offline again (sometimes happens every week or so).
     */
    private fun allowFailWithSocketTimeout(block: () -> Unit) = runCatching {
        block.invoke()
    }.getOrElse { ex ->
        if (ex is SocketTimeoutException) {
            println("A resolver URL is timing out: ${ex.message}")
        } else throw ex
    }

    open fun `given a did String, when calling resolve, then the result is a valid did document`(
        did: String,
        key: JsonObject,
        assert: resolverAssertion<DidDocument>,
    ) {
        allowFailWithSocketTimeout {
            val result = runBlocking { resolver.resolve(did) }
            assert(did, key, result)
        }
    }

    open fun `given a did String, when calling resolveToKey, then the result is valid key`(
        did: String,
        key: JsonObject,
        assert: resolverAssertion<Key>,
    ) {
        allowFailWithSocketTimeout {
            val result = runBlocking { resolver.resolveToKey(did) }
            assert(did, key, result)
        }
    }

    open fun `given a did String, when calling resolveToKeys, then the result is valid keys set`(
        did: String,
        key: JsonObject,
        assert: resolverAssertion<Set<Key>>,
    ) {
        allowFailWithSocketTimeout {
            val result = runBlocking { resolver.resolveToKeys(did) }
            assert(did, key, result)
        }
    }

    companion object {

        //region-DidDocument assertions-
        private val didDocAssertions: resolverAssertion<DidDocument> = { did, key, result ->
            val doc = result.getOrThrow()
            // assert [id] and [did] are identical
            assert(doc["id"]!!.jsonPrimitive.content == did)
            verificationMethodAssertions(doc, key) { v, k ->
                defaultKeyChecks(v, k)
            }
        }

        /**
         * Runs tests against **ed25519** specific fields.
         * Inherits [didDocAssertions]
         */
        val ed25519DidAssertions: resolverAssertion<DidDocument> = { did, key, result ->
            didDocAssertions(did, key, result)
            val doc = result.getOrNull()!!
            verificationMethodAssertions(doc, key) { v, k ->
                ed25519KeyChecks(v, k)
            }
        }

        /**
         * Runs tests against **secp256-k1/-r1** specific fields.
         * Inherits [ed25519DidAssertions]
         */
        val secp256DidAssertions: resolverAssertion<DidDocument> = { did, key, result ->
            ed25519DidAssertions(did, key, result)
            val doc = result.getOrNull()!!
            verificationMethodAssertions(doc, key) { v, k ->
                secp256KeyChecks(v, k)
            }
        }

        /**
         * Runs tests against **rsa** specific fields.
         * Inherits [didDocAssertions]
         */
        val rsaDidAssertions: resolverAssertion<DidDocument> = { did, key, result ->
            didDocAssertions(did, key, result)
            val doc = result.getOrNull()!!
            verificationMethodAssertions(doc, key) { v, k ->
                rsaKeyChecks(v, k)
            }
        }
        //endregion -DidDocument assertions-

        //region -Key assertions-
        private val keyAssertions: resolverAssertion<Key> = { did, key, result ->
            val keyResult = result.getOrNull()
            assert(result.isSuccess)
            assertNotNull(keyResult)
            val publicKey = runBlocking { keyResult.getPublicKey().exportJWKObject() }
            assert(defaultKeyChecks(publicKey, key))
        }

        /**
         * Runs tests against **ed25519** specific fields.
         * Inherits [keyAssertions]
         */
        val ed25519KeyAssertions: resolverAssertion<Key> = { did, key, result ->
            keyAssertions(did, key, result)
            val publicKey = runBlocking { result.getOrNull()!!.getPublicKey().exportJWKObject() }
            assert(ed25519KeyChecks(publicKey, key))
        }

        /**
         * Runs tests against **secp256-k1/-r1** specific fields.
         * Inherits [ed25519KeyAssertions]
         */
        val secp256KeyAssertions: resolverAssertion<Key> = { did, key, result ->
            ed25519KeyAssertions(did, key, result)
            val publicKey = runBlocking { result.getOrNull()!!.getPublicKey().exportJWKObject() }
            assert(secp256KeyChecks(publicKey, key))
        }

        /**
         * Runs tests against **rsa** specific fields.
         * Inherits [keyAssertions]
         */
        val rsaKeyAssertions: resolverAssertion<Key> = { did, key, result ->
            keyAssertions(did, key, result)
            val publicKey = runBlocking { result.getOrNull()!!.getPublicKey().exportJWKObject() }
            assert(rsaKeyChecks(publicKey, key))
        }

        //region -KeySet assertions-
        private val keysSetAssertions: resolverAssertion<Set<Key>> = { did, key, result ->
            val keysResult = result.getOrNull()
            assert(result.isSuccess)
            assertNotNull(keysResult)
            assert(keysResult.isNotEmpty())

            // At least one of the keys should match the expected key
            val foundMatchingKey = keysResult.any { resolvedKey ->
                val publicKey = runBlocking { resolvedKey.getPublicKey().exportJWKObject() }
                defaultKeyChecks(publicKey, key)
            }
            assert(foundMatchingKey)
        }

        /**
         * Runs tests against **ed25519** specific fields.
         * Inherits [keysSetAssertions]
         */
        val ed25519KeysSetAssertions: resolverAssertion<Set<Key>> = { did, key, result ->
            keysSetAssertions(did, key, result)
            val keysResult = result.getOrNull()!!

            // At least one of the keys should be an Ed25519 key matching the expected properties
            val foundMatchingKey = keysResult.any { resolvedKey ->
                val publicKey = runBlocking { resolvedKey.getPublicKey().exportJWKObject() }
                ed25519KeyChecks(publicKey, key)
            }
            assert(foundMatchingKey)
        }

        /**
         * Runs tests against **secp256-k1/-r1** specific fields.
         * Inherits [ed25519KeysSetAssertions]
         */
        val secp256KeysSetAssertions: resolverAssertion<Set<Key>> = { did, key, result ->
            ed25519KeysSetAssertions(did, key, result)
            val keysResult = result.getOrNull()!!

            // At least one of the keys should be a secp256 key matching the expected properties
            val foundMatchingKey = keysResult.any { resolvedKey ->
                val publicKey = runBlocking { resolvedKey.getPublicKey().exportJWKObject() }
                secp256KeyChecks(publicKey, key)
            }
            assert(foundMatchingKey)
        }

        /**
         * Runs tests against **rsa** specific fields.
         * Inherits [keysSetAssertions]
         */
        val rsaKeysSetAssertions: resolverAssertion<Set<Key>> = { did, key, result ->
            keysSetAssertions(did, key, result)
            val keysResult = result.getOrNull()!!

            // At least one of the keys should be an RSA key matching the expected properties
            val foundMatchingKey = keysResult.any { resolvedKey ->
                val publicKey = runBlocking { resolvedKey.getPublicKey().exportJWKObject() }
                rsaKeyChecks(publicKey, key)
            }
            assert(foundMatchingKey)
        }
        //endregion -KeySet assertions-
        //endregion -Key assertions-

        private val verificationMethodAssertions: (
            doc: DidDocument, key: JsonObject, runChecks: (actual: JsonObject, expected: JsonObject) -> Boolean,
        ) -> Unit = { doc, key, runChecks ->
            // verification method is optional
            doc["verificationMethod"]?.takeIf { it != JsonNull }?.run {
                assert(this.jsonArray.any {
                    runChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, key)
                })
            }
        }
    }
}

internal typealias resolverAssertion<T> = (did: String, key: JsonObject, result: Result<T>) -> Unit

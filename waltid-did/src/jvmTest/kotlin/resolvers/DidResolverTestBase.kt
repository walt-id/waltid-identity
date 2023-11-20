package resolvers

import DidDocumentChecks.defaultKeyChecks
import DidDocumentChecks.ed25519KeyChecks
import DidDocumentChecks.rsaKeyChecks
import DidDocumentChecks.secp256KeyChecks
import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.resolver.local.LocalResolverMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertNotNull

abstract class DidResolverTestBase {
    protected abstract val sut: LocalResolverMethod

    open fun `given a did String, when calling resolve, then the result is a valid did document`(
        did: String,
        key: JsonObject,
        assert: resolverAssertion<DidDocument>
    ) {
        val result = runBlocking { sut.resolve(did) }
        assert(did, key, result)
    }

    open fun `given a did String, when calling resolveToKey, then the result is valid key`(
        did: String,
        key: JsonObject,
        assert: resolverAssertion<Key>
    ) {
        val result = runBlocking { sut.resolveToKey(did) }
        assert(did, key, result)
    }

    companion object {

        //region-DidDocument assertions-
        private val didDocAssertions: resolverAssertion<DidDocument> = { did, key, result ->
            val doc = result.getOrNull()
            assert(result.isSuccess)
            assertNotNull(doc)
            // assert [id] and [did] are identical
            assert(doc["id"]!!.jsonPrimitive.content == did)
            assert(doc["verificationMethod"]!!.jsonArray.any {
                defaultKeyChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, key)
            })
        }

        /**
         * Runs tests against **ed25519** specific fields.
         * Inherits [didDocAssertions]
         */
        val ed25519DidAssertions: resolverAssertion<DidDocument> = { did, key, result ->
            didDocAssertions(did, key, result)
            val doc = result.getOrNull()!!
            assert(doc["verificationMethod"]!!.jsonArray.any {
                ed25519KeyChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, key)
            })
        }

        /**
         * Runs tests against **secp256-k1/-r1** specific fields.
         * Inherits [ed25519DidAssertions]
         */
        val secp256DidAssertions: resolverAssertion<DidDocument> = { did, key, result ->
            ed25519DidAssertions(did, key, result)
            val doc = result.getOrNull()!!
            assert(doc["verificationMethod"]!!.jsonArray.any {
                secp256KeyChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, key)
            })
        }

        /**
         * Runs tests against **rsa** specific fields.
         * Inherits [didDocAssertions]
         */
        val rsaDidAssertions: resolverAssertion<DidDocument> = { did, key, result ->
            didDocAssertions(did, key, result)
            val doc = result.getOrNull()!!
            assert(doc["verificationMethod"]!!.jsonArray.any {
                rsaKeyChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, key)
            })
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
        //endregion -Key assertions-
    }
}

internal typealias resolverAssertion<T> = (did: String, key: JsonObject, result: Result<T>) -> Unit

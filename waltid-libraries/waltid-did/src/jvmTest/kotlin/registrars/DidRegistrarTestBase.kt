package registrars

import DidDocumentChecks.defaultDidChecks
import DidDocumentChecks.defaultKeyChecks
import DidDocumentChecks.ed25519DidChecks
import DidDocumentChecks.ed25519KeyChecks
import DidDocumentChecks.rsaDidChecks
import DidDocumentChecks.rsaKeyChecks
import DidDocumentChecks.secp256DidChecks
import DidDocumentChecks.secp256KeyChecks
import id.walt.crypto.keys.Key
import id.walt.did.dids.DidUtils
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertTrue

abstract class DidRegistrarTestBase(private val registrar: LocalRegistrarMethod) {

    open fun `given did options with no key when register then returns a valid did result`(
        options: DidCreateOptions,
        registrarDidAssertion: registrarDidAssertion,
    ) {
        val docResult = runBlocking { registrar.register(options) }
        registrarDidAssertion(docResult, options)
    }

    open fun `given did options and key when register with key then returns a valid did result`(
        key: Key,
        options: DidCreateOptions,
        registrarKeyAssertion: registrarKeyAssertion,
    ) {
        val docResult = runBlocking { registrar.registerByKey(key, options) }
        val publicKey = runBlocking { key.getPublicKey() }
        registrarKeyAssertion(docResult, options, publicKey)
    }

    companion object {

        private val didAssertions: registrarDidAssertion = { result, options ->
            val did = result.did
            val doc = result.didDocument.toJsonObject()
            // assert [did]
            assertTrue(did.isNotEmpty())
            // assert [did] method type
            assertTrue(DidUtils.methodFromDid(did).equals(options.method, true))
            // assert [id] and [did] are identical
            assertTrue(doc["id"]!!.jsonPrimitive.content == did)
            // assert [verificationMethod id] and [did] are identical
            assertTrue(doc["verificationMethod"]!!.jsonArray.any {
                it.jsonObject["id"]!!.jsonPrimitive.content.substringBefore("#") == did
            })
            // assert [verificationMethod controller] and [did] are identical
            assertTrue(doc["verificationMethod"]!!.jsonArray.any {
                it.jsonObject["controller"]!!.jsonPrimitive.content == did
            })
            // assert [assertionMethod] is an array with elements
            assertTrue(!doc["assertionMethod"]!!.jsonArray.isEmpty())
            // assert [authentication] is an array with elements
            assertTrue(!doc["assertionMethod"]!!.jsonArray.isEmpty())
            // assert [capabilityInvocation] is an array with elements
            assertTrue(!doc["capabilityInvocation"]!!.jsonArray.isEmpty())
            // assert [capabilityDelegation] is an array with elements
            assertTrue(!doc["capabilityDelegation"]!!.jsonArray.isEmpty())
            // assert [keyAgreement] is an array with elements
            assertTrue(!doc["keyAgreement"]!!.jsonArray.isEmpty())
        }

        //region -Did assertions-
        val defaultDidAssertions: registrarDidAssertion = { result, options ->
            didAssertions(result, options)
            val doc = result.didDocument.toJsonObject()
            assertTrue(doc["verificationMethod"]!!.jsonArray.none {
                defaultDidChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject)
            })
        }

        val ed25519DidAssertions: registrarDidAssertion = { result, options ->
            defaultDidAssertions(result, options)
            val doc = result.didDocument.toJsonObject()
            assertTrue(doc["verificationMethod"]!!.jsonArray.none {
                ed25519DidChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject)
            })
        }

        val secpDidAssertions: registrarDidAssertion = { result, options ->
            ed25519DidAssertions(result, options)
            val doc = result.didDocument.toJsonObject()
            assertTrue(doc["verificationMethod"]!!.jsonArray.none {
                secp256DidChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject)
            })
        }

        val rsaDidAssertions: registrarDidAssertion = { result, options ->
            defaultDidAssertions(result, options)
            val doc = result.didDocument.toJsonObject()
            assertTrue(doc["verificationMethod"]!!.jsonArray.none {
                rsaDidChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject)
            })
        }
        //endregion -Did assertions-

        //region -Key assertions-
        val defaultKeyAssertions: registrarKeyAssertion = { result, options, key ->
            defaultDidAssertions(result, options)
            val doc = result.didDocument.toJsonObject()
            val publicKey = runBlocking { key.getPublicKey().exportJWKObject() }
            // assert [verificationMethod publicKeyJwk kid] and [key keyId] are identical
            assertTrue(doc["verificationMethod"]!!.jsonArray.any {
                defaultKeyChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, publicKey)
            })
        }

        val ed25519KeyAssertions: registrarKeyAssertion = { result, options, key ->
            defaultKeyAssertions(result, options, key)
            val doc = result.didDocument.toJsonObject()
            val publicKey = runBlocking { key.getPublicKey().exportJWKObject() }
            assertTrue(doc["verificationMethod"]!!.jsonArray.any {
                ed25519KeyChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, publicKey)
            })
        }

        val secpKeyAssertions: registrarKeyAssertion = { result, options, key ->
            ed25519KeyAssertions(result, options, key)
            val doc = result.didDocument
            val publicKey = runBlocking { key.getPublicKey().exportJWKObject() }
            assertTrue(doc["verificationMethod"]!!.jsonArray.any {
                secp256KeyChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, publicKey)
            })
        }

        val rsaKeyAssertions: registrarKeyAssertion = { result, options, key ->
            defaultKeyAssertions(result, options, key)
            val doc = result.didDocument.toJsonObject()
            val publicKey = runBlocking { key.getPublicKey().exportJWKObject() }
            assertTrue(doc["verificationMethod"]!!.jsonArray.any {
                rsaKeyChecks(it.jsonObject["publicKeyJwk"]!!.jsonObject, publicKey)
            })
        }
        //endregion -Key assertions-
    }
}
internal typealias registrarDidAssertion = (did: DidResult, options: DidCreateOptions) -> Unit
internal typealias registrarKeyAssertion = (did: DidResult, options: DidCreateOptions, publicKey: Key) -> Unit

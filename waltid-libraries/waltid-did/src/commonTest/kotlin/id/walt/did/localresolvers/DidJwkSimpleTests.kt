package id.walt.did.localresolvers

import id.walt.crypto.keys.KeyType
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.did.dids.resolver.local.DidJwkResolver
import id.walt.did.localresolvers.LocalResolverTestUtils.testDids
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

class DidJwkSimpleTests {

    @OptIn(ExperimentalEncodingApi::class)
    private fun testDids(vararg dids: String, expectedKeyType: KeyType) =
        runTest {
            val results = testDids(DidJwkResolver(), dids = dids, expectedKeyType = expectedKeyType)

            val registrar = DidJwkRegistrar()


            fun decodeDid(did: String) = Json.parseToJsonElement(Base64.decode(did.removePrefix("did:jwk:")).decodeToString())


            println("\nRegistration tests:")
            val registrations = results.mapValues {
                val s = registrar.registerByKey(it.value.getOrThrow(), DidCreateOptions(registrar.method, emptyMap())).did

                decodeDid(s)
            }

            registrations.forEach { (expected, actual) ->
                println("${decodeDid(expected)} -> $actual")
                assertEquals(decodeDid(expected), actual)
            }
        }

    /*@Test
    fun testEd25519() = testDids(
        "",
        expectedKeyType = KeyType.Ed25519
    )*/

    /*@Test
    fun testSecp256k1() = testDids(
        "",
        expectedKeyType = KeyType.secp256k1
    )*/

    @Test
    fun testP256() = testDids(
        "did:jwk:eyJjcnYiOiJQLTI1NiIsImt0eSI6IkVDIiwieCI6ImFjYklRaXVNczNpOF91c3pFakoydHBUdFJNNEVVM3l6OTFQSDZDZEgyVjAiLCJ5IjoiX0tjeUxqOXZXTXB0bm1LdG00NkdxRHo4d2Y3NEk1TEtncmwyR3pIM25TRSJ9",
        expectedKeyType = KeyType.secp256r1
    )

    /*@Test
    fun testRSA() = testDids(
        "",
        expectedKeyType = KeyType.RSA
    )*/
}

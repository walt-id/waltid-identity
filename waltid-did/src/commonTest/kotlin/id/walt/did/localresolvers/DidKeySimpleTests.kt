package id.walt.did.localresolvers

import id.walt.crypto.keys.KeyType
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.resolver.local.DidKeyResolver
import id.walt.did.localresolvers.LocalResolverTestUtils.testDids
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DidKeySimpleTests {

    private fun testDids(vararg dids: String, expectedKeyType: KeyType) =
        runTest {
            val results = testDids(DidKeyResolver(), dids = dids, expectedKeyType = expectedKeyType)

            val registrar = DidKeyRegistrar()

            println("\nRegistration tests:")
            val registrations = results.mapValues {
                registrar.registerByKey(it.value.getOrThrow(), DidCreateOptions(registrar.method, emptyMap())).did
            }

            registrations.forEach { (expected, actual) ->
                println("Expected: $expected")
                println("Actual:   $actual")
                println()
            }

            registrations.forEach { (expected, actual) ->
                assertEquals(expected, actual)
            }
        }

    //FIXME @Test
    fun testEd25519() = testDids(
        "did:key:z6MkiTBz1ymuepAQ4HEHYSF1H8quG5GLVVQR3djdX3mDooWp",
        "did:key:z6MkjchhfUsD6mmvni8mCdXHw216Xrm9bQe2mBH1P5RDjVJG",
        "did:key:z6MknGc3ocHs3zdPiJbnaaqDi58NGb4pk1Sp9WxWufuXSdxf",
        expectedKeyType = KeyType.Ed25519
    )

    // TODO enable:
    /*@Test
    fun testSecp256k1() = testDids(
        "did:key:zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme",
        "did:key:zQ3shtxV1FrJfhqE1dvxYRcCknWNjHc3c5X1y3ZSoPDi2aur2",
        "did:key:zQ3shZc2QzApp2oymGvQbzP8eKheVshBHbU4ZYjeXqwSKEn6N",
        expectedKeyType = KeyType.secp256k1
    )

    @Test
    fun testP256() = testDids(
        "did:key:zDnaerDaTF5BXEavCrfRZEk316dpbLsfPDZ3WJ5hRTPFU2169",
        "did:key:zDnaerx9CtbPJ1q36T5Ln5wYt3MQYeGRG5ehnPAmxcf5mDZpv",
        expectedKeyType = KeyType.secp256r1
    )

    @Test
    fun testRSA() = testDids(
        "did:key:z4MXj1wBzi9jUstyPMS4jQqB6KdJaiatPkAtVtGc6bQEQEEsKTic4G7Rou3iBf9vPmT5dbkm9qsZsuVNjq8HCuW1w24nhBFGkRE4cd2Uf2tfrB3N7h4mnyPp1BF3ZttHTYv3DLUPi1zMdkULiow3M1GfXkoC6DoxDUm1jmN6GBj22SjVsr6dxezRVQc7aj9TxE7JLbMH1wh5X3kA58H3DFW8rnYMakFGbca5CB2Jf6CnGQZmL7o5uJAdTwXfy2iiiyPxXEGerMhHwhjTA1mKYobyk2CpeEcmvynADfNZ5MBvcCS7m3XkFCMNUYBS9NQ3fze6vMSUPsNa6GVYmKx2x6JrdEjCk3qRMMmyjnjCMfR4pXbRMZa3i",
        expectedKeyType = KeyType.RSA
    )*/
}

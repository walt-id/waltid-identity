package id.walt.did

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.TSEKey
import id.walt.crypto.keys.TSEKeyMetadata
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.helpers.WaltidServices
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class DidTest {

    val remoteKey = false

    @BeforeTest
    fun init() {
        runTest(timeout = 1.minutes) {
            WaltidServices.init()
        }
    }

    private fun groupDidList(resolverMethods: Map<String, String>): Map<String, List<String>> =
        resolverMethods.toList().groupBy { it.second }.mapValues { it.value.map { it.first } }

    @Test
    fun listDidMethods() {
//        println("Registrar: " + DidService.registrarMethods.toList().groupBy { it.second.name })
//        println("Resolver: " + DidService.resolverMethods.toList().groupBy { it.second.name })

        println("Resolver:")
        println(
            groupDidList(DidService.resolverMethods.mapValues { it.value.name })
        )

        println("Registrar:")
        println(
            groupDidList(DidService.registrarMethods.mapValues { it.value.name })
        )
    }


    val tseMetadata = TSEKeyMetadata("http://127.0.0.1:8200/v1/transit", "dev-only-token")

    @Test
    fun createDidJwk() = runTest {

        val key = if (remoteKey) TSEKey.generate(KeyType.Ed25519, tseMetadata) else LocalKey.generate(KeyType.Ed25519)

        val did = DidService.registerByKey("jwk", key)

        println(did.didDocument.toJsonObject())
    }

    @Test
    fun createDidKeyJcs() = runTest {

        val key = if (remoteKey) TSEKey.generate(KeyType.Ed25519, tseMetadata) else LocalKey.generate(KeyType.Ed25519)

        val options = DidKeyCreateOptions(KeyType.Ed25519, useJwkJcsPub = true)
        val did = DidService.registerByKey("key", key, options)

        println(did.didDocument.toJsonObject())
    }
}

package id.walt.did

import id.walt.crypto.keys.tse.TSEKeyMetadata
import id.walt.did.dids.DidService
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class DidExamples {

    private val tseMetadata = TSEKeyMetadata("http://127.0.0.1:8200/v1/transit", "dev-only-token")

    @BeforeTest
    fun init() {
        runTest(timeout = 1.minutes) {
            DidService.init()
        }
    }

    private suspend fun isVaultAvailable() = runCatching {
        HttpClient().get("http://127.0.0.1:8200").status == HttpStatusCode.OK
    }.fold(onSuccess = { it }, onFailure = { false })

    private fun groupDidList(resolverMethods: Map<String, String>): Map<String, List<String>> =
        resolverMethods.toList().groupBy { it.second }.mapValues { it.value.map { it.first } }

    @Test
    fun listDidMethods() {
        println("Resolver:")
        println(
            groupDidList(DidService.resolverMethods.mapValues { it.value.name })
        )

        println("Registrar:")
        println(
            groupDidList(DidService.registrarMethods.mapValues { it.value.name })
        )
    }

//    @Test
//    fun exampleCreateDidJwk() = runTest {
//
//        val key = if (isVaultAvailable()) TSEKey.generate(
//            KeyType.Ed25519, tseMetadata
//        ) else JWKKey.generate(KeyType.Ed25519)
//
//        val did = DidService.registerByKey("jwk", key)
//
//        println(did.didDocument.toJsonObject())
//    }

    /**
     * did:key according W3C CCG https://w3c-ccg.github.io/did-method-key/
     */
//    @Test
//    fun exampleCreateDidKey() = runTest {
//
//        val key = if (isVaultAvailable()) TSEKey.generate(
//            KeyType.Ed25519, tseMetadata
//        ) else JWKKey.generate(KeyType.Ed25519)
//
//        val options = DidKeyCreateOptions(KeyType.Ed25519, useJwkJcsPub = false)
//        val did = DidService.registerByKey("key", key, options)
//
//        println("DID ${did.did}")
//        println("DID Doc: ${did.didDocument.toJsonObject()}")
//    }

    /**
     * did:key (jwk_jcs-pub) according EBSI https://hub.ebsi.eu/tools/libraries/key-did-resolver
     */
//    @Test
//    fun exampleCreateDidKeyJcs() = runTest {
//
//        val key = if (isVaultAvailable()) TSEKey.generate(
//            KeyType.Ed25519, tseMetadata
//        ) else JWKKey.generate(KeyType.Ed25519)
//
//        val options = DidKeyCreateOptions(KeyType.Ed25519, useJwkJcsPub = true)
//        val did = DidService.registerByKey("key", key, options)
//
//        println("DID ${did.did}")
//        println("DID Doc: ${did.didDocument.toJsonObject()}")
//    }

//    @Test
//    fun exampleResolveDIDs() = runTest {
//
//        DidService.resolve("did:key:z6MkozXULNk2ax6zAmJBGgrGod2DB3RXuWNJPm8BVZkmmVSA").let { println("did:key: $it")  }
//        DidService.resolve("did:ebsi:ziE2n8Ckhi6ut5Z8Cexrihd").let { println("did:ebsi: $it")  }
//        DidService.resolve("did:ion:EiClkZMDxPKqC9c-umQfTkR8vvZ9JPhl_xLDI9Nfk38w5w").let { println("did:ion: $it")  }
//        DidService.resolve("did:web:did.actor:alice").let { println("did:web: $it")  }
//        DidService.resolve("did:pkh:tz:tz2BFTyPeYRzxd5aiBchbXN3WCZhx7BqbMBq").let { println("did:pkh: $it")  }
//        DidService.resolve("did:ens:vitalik.eth").let { println("did:ens: $it")  }
//
//    }

}

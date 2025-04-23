package id.walt.entrawallet.core

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.helpers.WaltidServices
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking

abstract class BaseTest {
    protected val http = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    protected var key: Key
    protected var did: String

    init {
        runBlocking {
            WaltidServices.minimalInit()

            key = JWKKey.generate(KeyType.secp256r1)
            did = DidService.registerByKey("jwk", key).did
        }

    }
}

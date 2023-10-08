package id.walt.ssikit.did.registrar

import id.walt.core.crypto.keys.Key
import id.walt.ssikit.did.registrar.dids.DidCreateOptions
import id.walt.ssikit.did.registrar.dids.DidEbsiCreateOptions
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

class UniregistrarRegistrar : DidRegistrar {

    @Suppress("MemberVisibilityCanBePrivate")
    //var registrarUrl = "http://localhost:9080/1.0"
    var registrarUrl = "https://uniregistrar.io/1.0"

    override val name = "uniresolver @ $registrarUrl"


    private val http = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        defaultRequest {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }

    override suspend fun getSupportedMethods() = runCatching { lazyOf(getMethods()).value }

    private suspend fun getMethods(): Set<String> =
        http.get("$registrarUrl/methods")
            .body<JsonArray>()
            .map { it.jsonPrimitive.content }
            .toSet()

    override suspend fun create(didCreate: DidCreateOptions): DidResult {
        return DidResult("TODO" /* TODO */, http.post("$registrarUrl/create?method=${didCreate.method}") {
            setBody(didCreate.options)
        }.body())
    }

    override suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult {
        TODO("Not yet implemented")
    }

    override suspend fun update() {
        TODO("Not yet implemented")
    }

    override suspend fun delete() {
        TODO("Not yet implemented")
    }
}

suspend fun main() {
    //println(UniregistrarRegistrar().getSupportedMethods())
    //println(UniregistrarRegistrar().create(DidKeyCreateOptions(KeyType.Ed25519)))
    println(UniregistrarRegistrar().create(DidEbsiCreateOptions("eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QiLCJraWQiOiJkaWQ6ZWJzaTp6cjJyV0RISHJVQ2RaQVc3d3NTYjVuUSNrZXlzLTEifQ.eyJvbmJvYXJkaW5nIjoicmVjYXB0Y2hhIiwidmFsaWRhdGVkSW5mbyI6eyJzdWNjZXNzIjp0cnVlLCJjaGFsbGVuZ2VfdHMiOiIyMDIzLTA3LTI2VDA5OjA4OjM1WiIsImhvc3RuYW1lIjoiYXBwLXBpbG90LmVic2kuZXUiLCJzY29yZSI6MC45LCJhY3Rpb24iOiJsb2dpbiJ9LCJpc3MiOiJkaWQ6ZWJzaTp6cjJyV0RISHJVQ2RaQVc3d3NTYjVuUSIsImlhdCI6MTY5MDM2MjUxNiwiZXhwIjoxNjkwMzYzNDE2fQ.SBKaY7rBn_CrEQ_PerbIFICobSMuUBwejxfAymDEbSgnbKz8HfJyLv_DP8OZA5tCW00GIvSODUX2GliA-dXEHA")))
}

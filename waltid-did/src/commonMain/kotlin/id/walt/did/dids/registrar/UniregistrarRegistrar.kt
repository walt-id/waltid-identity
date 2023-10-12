package id.walt.did.dids.registrar

import id.walt.crypto.keys.Key
import id.walt.did.dids.registrar.dids.DidCreateOptions
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

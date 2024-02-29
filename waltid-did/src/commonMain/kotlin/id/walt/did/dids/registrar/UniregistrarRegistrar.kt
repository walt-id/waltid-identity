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
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getSupportedMethods() = runCatching { lazyOf(getMethods()).value }

    private suspend fun getMethods(): Set<String> =
        http.get("$registrarUrl/methods")
            .body<JsonArray>()
            .map { it.jsonPrimitive.content }
            .toSet()

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun create(options: DidCreateOptions): DidResult {
        return DidResult("TODO" /* TODO */, http.post("$registrarUrl/create?method=${options.method}") {
            setBody(options.options)
        }.body())
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult {
        TODO("Not yet implemented")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun update() {
        TODO("Not yet implemented")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun delete() {
        TODO("Not yet implemented")
    }
}

package id.walt.did.dids.registrar

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.LoggingConfiguration
import io.ktor.client.plugins.logging.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * @param registrarUrl Resolver URL, e.g. "http://localhost:9080/1.0"
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class UniregistrarRegistrar(var registrarUrl: String = DEFAULT_REGISTRAR_URL) : DidRegistrar {

    companion object {
        const val DEFAULT_REGISTRAR_URL = "https://uniregistrar.io/1.0"
    }

    override val name = "uniresolver @ $registrarUrl"

    private val fetcher = WebDataFetcher(
        id = "did-uniregistrar",
        defaultConfiguration = WebDataFetchingConfiguration(
            logging = LoggingConfiguration(enable = true, level = LogLevel.ALL)
        )
    )

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getSupportedMethods() = runCatching { lazyOf(getMethods()).value }

    private suspend fun getMethods(): Set<String> =
        fetcher.fetch<JsonArray>("$registrarUrl/methods").body
            .map { it.jsonPrimitive.content }
            .toSet()

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun create(options: DidCreateOptions): DidResult {
        return DidResult("TODO" /* TODO */, fetcher.send<_, DidDocument>("$registrarUrl/create?method=${options.method}", options.config).body)
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

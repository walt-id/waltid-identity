package id.walt.did.dids.registrar

import id.walt.crypto.keys.Key
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.cheqd.DidCheqdRegistrar
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class LocalRegistrar : DidRegistrar {
    override val name = "walt.id local registrar"

    private val registrarMethods = setOf(
        DidJwkRegistrar(),
        DidKeyRegistrar(),
        DidWebRegistrar(),
        DidCheqdRegistrar(),
    ).associateBy { it.method }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getSupportedMethods() = Result.success(setOf("key", "jwk", "web", "cheqd" /*"ebsi",*/))
    //override suspend fun getSupportedMethods() = Result.success(registrarMethods.values.toSet())

    private fun getRegistrarForMethod(method: String) =
        registrarMethods[method] ?: throw IllegalArgumentException("No local registrar for method: $method")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun create(options: DidCreateOptions): DidResult =
        getRegistrarForMethod(options.method).register(options)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult =
        getRegistrarForMethod(options.method).registerByKey(key, options)


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

suspend fun main() {
    LocalRegistrar().create(DidWebCreateOptions("localhost", "/wallet-api/registry/1237"))
        .also { println(it.didDocument) }
}

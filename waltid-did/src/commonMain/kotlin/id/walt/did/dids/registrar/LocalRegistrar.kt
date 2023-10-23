package id.walt.did.dids.registrar

import id.walt.crypto.keys.Key
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.cheqd.DidCheqdRegistrar
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.registrar.local.web.DidWebRegistrar

class LocalRegistrar : DidRegistrar {
    override val name = "ssikit2 local registrar"

    private val registrarMethods = setOf(
        DidJwkRegistrar(),
        DidKeyRegistrar(),
        DidWebRegistrar(),
        DidCheqdRegistrar(),
    ).associateBy { it.method }

    override suspend fun getSupportedMethods() = Result.success(setOf("key", "jwk", "web", "cheqd" /*"ebsi",*/))
    //override suspend fun getSupportedMethods() = Result.success(registrarMethods.values.toSet())

    private fun getRegistrarForMethod(method: String) =
        registrarMethods[method] ?: throw IllegalArgumentException("No local registrar for method: $method")

    override suspend fun create(options: DidCreateOptions): DidResult =
        getRegistrarForMethod(options.method).register(options)
    override suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult =
        getRegistrarForMethod(options.method).registerByKey(key, options)


    override suspend fun update() {
        TODO("Not yet implemented")
    }

    override suspend fun delete() {
        TODO("Not yet implemented")
    }

}

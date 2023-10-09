package id.walt.didlib.did.registrar

import id.walt.core.crypto.keys.Key
import id.walt.didlib.did.registrar.dids.DidCreateOptions
import id.walt.didlib.did.registrar.local.jwk.DidJwkRegistrar
import id.walt.didlib.did.registrar.local.key.DidKeyRegistrar
import id.walt.didlib.did.registrar.local.web.DidWebRegistrar

class LocalRegistrar : DidRegistrar {
    override val name = "ssikit2 local registrar"

    private val registrarMethods = setOf(
        DidJwkRegistrar(),
        DidKeyRegistrar(),
        DidWebRegistrar(),
    ).associateBy { it.method }

    override suspend fun getSupportedMethods() = Result.success(setOf("key", "jwk", "web", "ebsi", "cheqd"))
    //override suspend fun getSupportedMethods() = Result.success(registrarMethods.values.toSet())

    private fun getRegistrarForMethod(method: String) =
        registrarMethods[method] ?: throw IllegalArgumentException("No local registrar for method: $method")

    override suspend fun create(options: DidCreateOptions): DidResult =
        getRegistrarForMethod(options.method).register(options)
    override suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult =
        getRegistrarForMethod(options.method).registerByKey(key.getPublicKey(), options)


    override suspend fun update() {
        TODO("Not yet implemented")
    }

    override suspend fun delete() {
        TODO("Not yet implemented")
    }

}

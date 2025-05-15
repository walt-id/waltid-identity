package id.walt.did.dids.registrar.java

import id.walt.did.dids.registrar.DidRegistrar

abstract class JavaDidRegistrar : DidRegistrar {

    abstract fun javaGetSupportedMethods(): Set<String>
    override suspend fun getSupportedMethods(): Result<Set<String>> =
        runCatching { javaGetSupportedMethods() }

    //override suspend fun create(options: DidCreateOptions): DidResult
    //override suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult
    //override suspend fun update()
    //override suspend fun delete()

}

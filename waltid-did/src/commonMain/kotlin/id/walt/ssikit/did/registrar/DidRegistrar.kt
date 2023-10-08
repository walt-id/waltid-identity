package id.walt.ssikit.did.registrar

import id.walt.core.crypto.keys.Key
import id.walt.ssikit.did.registrar.dids.DidCreateOptions

interface DidRegistrar {

    val name: String

    suspend fun getSupportedMethods(): Result<Set<String>>

    suspend fun create(options: DidCreateOptions): DidResult
    suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult
    suspend fun update()
    suspend fun delete()

}

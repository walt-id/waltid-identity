package id.walt.did.dids.registrar

import id.walt.core.crypto.keys.Key
import id.walt.did.dids.registrar.dids.DidCreateOptions

interface DidRegistrar {

    val name: String

    suspend fun getSupportedMethods(): Result<Set<String>>

    suspend fun create(options: DidCreateOptions): DidResult
    suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult
    suspend fun update()
    suspend fun delete()

}

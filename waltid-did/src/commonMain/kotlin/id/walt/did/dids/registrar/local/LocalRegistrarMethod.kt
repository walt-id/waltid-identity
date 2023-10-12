package id.walt.did.dids.registrar.local

import id.walt.crypto.keys.Key
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions

abstract class LocalRegistrarMethod(val method: String) {

    abstract suspend fun register(options: DidCreateOptions): DidResult
    abstract suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult

}

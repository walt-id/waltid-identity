package id.walt.didlib.did.registrar.local

import id.walt.core.crypto.keys.Key
import id.walt.didlib.did.registrar.DidResult
import id.walt.didlib.did.registrar.dids.DidCreateOptions

abstract class LocalRegistrarMethod(val method: String) {

    abstract suspend fun register(options: DidCreateOptions): DidResult
    abstract suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult

}

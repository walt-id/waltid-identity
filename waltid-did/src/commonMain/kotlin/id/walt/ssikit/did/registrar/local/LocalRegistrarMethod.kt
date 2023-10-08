package id.walt.ssikit.did.registrar.local

import id.walt.core.crypto.keys.Key
import id.walt.ssikit.did.registrar.DidResult
import id.walt.ssikit.did.registrar.dids.DidCreateOptions

abstract class LocalRegistrarMethod(val method: String) {

    abstract suspend fun register(options: DidCreateOptions): DidResult
    abstract suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult

}

package id.walt.did.dids.registrar

import id.walt.crypto2.keys.Key
import id.walt.did.dids.registrar.dids.DidCreateOptions

/**
 * Native crypto2 DID registration contract.
 *
 * Implementations consume the original crypto2 key and export only its public material.
 */
fun interface Crypto2DidRegistrar {
    suspend fun createByKey(key: Key, options: DidCreateOptions): DidResult
}

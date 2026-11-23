package id.walt.did.dids

import id.walt.crypto2.keys.Key
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import kotlinx.serialization.json.JsonObject

/** Crypto2-first DID registration, document resolution, and verification-key resolution service. */
interface Crypto2DidService {
    suspend fun resolve(did: String): Result<JsonObject>

    suspend fun resolveToKeys(did: String): Result<Set<Key>>

    suspend fun registerByKey(
        method: String,
        key: Key,
        options: DidCreateOptions = DidCreateOptions(method, emptyMap()),
    ): DidResult

    companion object : Crypto2DidService {
        override suspend fun resolve(did: String): Result<JsonObject> = DidService.resolve(did)

        override suspend fun resolveToKeys(did: String): Result<Set<Key>> = DidService.resolveToCrypto2Keys(did)

        override suspend fun registerByKey(method: String, key: Key, options: DidCreateOptions): DidResult =
            DidService.registerByKey(method, key, options)
    }
}

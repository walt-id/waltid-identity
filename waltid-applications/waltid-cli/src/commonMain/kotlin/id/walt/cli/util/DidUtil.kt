package id.walt.cli.util

import id.walt.crypto2.keys.Key
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.DidService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class DidUtil {

    companion object {
        init {
            runBlocking {
                DidService.minimalInit()
            }
        }

        fun createDid(
            method: DidMethod,
            key: Key,
            options: DidCreateOptions? = null
        ): DidResult {
            return runBlocking {
                if (options != null)
                    Crypto2DidService.registerByKey(method.name.lowercase(), key, options)
                else
                    Crypto2DidService.registerByKey(method.name.lowercase(), key)
            }
        }

        fun resolveDid(did: String): JsonObject {
            return runBlocking {
                Crypto2DidService.resolve(did).getOrThrow()
            }
        }

        fun resolveToKeys(did: String): Set<Key> {
            return runBlocking {
                Crypto2DidService.resolveToKeys(did).getOrThrow()
            }
        }
    }
}

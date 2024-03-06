package id.walt.cli.util

import id.walt.crypto.keys.LocalKey
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.resolver.LocalResolver
import kotlinx.coroutines.runBlocking

class DidUtil {

    companion object {
        init {
            runBlocking {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                    registerRegistrar(LocalRegistrar())
                    updateRegistrarsForMethods()
                }
            }
        }


        fun createDid(method: DidMethod, key: LocalKey): DidResult {
            return runBlocking {
                DidService.registerByKey(method.name.lowercase(), key) //, options)
            }

            // val keyId = args["keyId"]?.content?.takeIf { it.isNotEmpty() } ?: generateKey(KeyType.Ed25519.name)
            // val key = getKey(keyId)
            // val options = getDidOptions(method, args)
            // val result = DidService.registerByKey(method, key, options)
            // DidsService.add(
            //     wallet = walletId,
            //     did = result.did,
            //     document = Json.encodeToString(result.didDocument),
            //     alias = args["alias"]?.content,
            //     keyId = keyId
            // )
            // logEvent(
            //     EventType.Did.Create, "wallet", DidEventData(
            //         did = result.did, document = result.didDocument.toString()
            //     )
            // )
            // return result.did
        }

        fun getSupportedMethods(): Set<String> {
            val methods = mutableSetOf<String>()
            for (registrar in DidService.didRegistrars) {
                runBlocking {
                    methods.addAll(registrar.getSupportedMethods().getOrThrow())
                }
            }

            return methods
        }
    }
}
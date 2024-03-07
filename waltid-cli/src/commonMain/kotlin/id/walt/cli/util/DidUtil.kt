package id.walt.cli.util

import id.walt.crypto.keys.LocalKey
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.DidResult
import kotlinx.coroutines.runBlocking

class DidUtil {

    companion object {
        init {
            runBlocking {
                // TODO: What exactly is needed?
                // DidService.apply {
                //     registerResolver(LocalResolver())
                //     updateResolversForMethods()
                //     registerRegistrar(LocalRegistrar())
                //     updateRegistrarsForMethods()
                // }
                DidService.minimalInit()
            }
        }

        fun createDid(method: DidMethod, key: LocalKey): DidResult {
            return runBlocking {
                DidService.registerByKey(method.name.lowercase(), key) //, options)
            }
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
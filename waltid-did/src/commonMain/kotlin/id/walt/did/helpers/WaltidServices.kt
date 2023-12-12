package id.walt.did.helpers

import id.walt.did.dids.DidService

object WaltidServices {

    suspend fun init() {
        DidService.init()
    }

    suspend fun minimalInit() {
        DidService.minimalInit()
    }
}

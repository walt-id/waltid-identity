package id.walt.ssikit.helpers

import id.walt.ssikit.did.DidService

object WaltidServices {

    suspend fun init() {
        DidService.init()
    }
}

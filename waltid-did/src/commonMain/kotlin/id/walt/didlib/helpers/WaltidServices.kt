package id.walt.didlib.helpers

import id.walt.didlib.did.DidService

object WaltidServices {

    suspend fun init() {
        DidService.init()
    }
}

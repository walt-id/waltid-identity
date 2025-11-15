package id.walt.did.helpers

import id.walt.did.dids.DidService
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Deprecated("Use DidService instead")
@OptIn(ExperimentalJsExport::class)
@JsExport
object WaltidServices {

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun init() {
        DidService.init()
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun minimalInit() {
        DidService.minimalInit()
    }
}

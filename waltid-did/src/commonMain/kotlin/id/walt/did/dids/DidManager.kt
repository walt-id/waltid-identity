package id.walt.did.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
interface DidManager {

    fun resolve(did: String)

    fun create(type: String)
    fun update()
    fun delete(did: String)
}

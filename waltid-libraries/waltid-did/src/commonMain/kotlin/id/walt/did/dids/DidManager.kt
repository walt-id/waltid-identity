package id.walt.did.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface DidManager {

    fun resolve(did: String)

    fun create(type: String)
    fun update()
    fun delete(did: String)
}

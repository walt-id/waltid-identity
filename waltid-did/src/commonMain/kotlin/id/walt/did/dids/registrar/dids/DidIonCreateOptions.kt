package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
class DidIonCreateOptions : DidCreateOptions(
    method = "ion",
    options = emptyMap()
)

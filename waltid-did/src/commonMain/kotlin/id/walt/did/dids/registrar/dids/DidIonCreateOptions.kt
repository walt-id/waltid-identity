package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidIonCreateOptions : DidCreateOptions(
    method = "ion",
    config = emptyMap()
)

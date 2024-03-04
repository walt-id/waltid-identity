package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
class DidBtcrCreateOptions(chain: String) : DidCreateOptions(
    method = "btcr",
    options = options("chain" to chain)
)

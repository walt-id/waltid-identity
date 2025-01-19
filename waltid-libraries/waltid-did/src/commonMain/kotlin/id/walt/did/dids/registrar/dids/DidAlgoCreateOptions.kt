package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidAlgoCreateOptions(network: String) : DidCreateOptions(
    method = "algo",
    config = mapOf("network" to network)
)

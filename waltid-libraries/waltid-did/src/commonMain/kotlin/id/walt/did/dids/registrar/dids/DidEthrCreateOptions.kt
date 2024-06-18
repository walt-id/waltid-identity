package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidEthrCreateOptions(network: String = "goerli") : DidCreateOptions(
    method = "ethr",
    config = config("network" to network)
)


package id.walt.did.dids.registrar

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object DidRegistrarRegistrations {

    val didRegistrars = setOf(
        LocalRegistrar(),
        UniregistrarRegistrar()
    )

}

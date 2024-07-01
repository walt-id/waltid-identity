package id.walt.did.dids.registrar

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object DidRegistrarRegistrations {

    fun curatedDidRegistrars(uniregistrarUrl: String? = null) = setOf(
        LocalRegistrar(),
        UniregistrarRegistrar(uniregistrarUrl ?: UniregistrarRegistrar.DEFAULT_REGISTRAR_URL)
    )

}

package id.walt.did.dids.resolver

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
object DidResolverRegistrations {

    val didResolvers = setOf(
        LocalResolver(),
        UniresolverResolver()
    )

}

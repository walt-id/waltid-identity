package id.walt.did.dids.resolver

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object DidResolverRegistrations {

    val didResolvers = setOf(
        LocalResolver(),
        UniresolverResolver()
    )

}

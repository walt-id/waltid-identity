package id.walt.did.dids.resolver

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object DidResolverRegistrations {

    fun curatedDidResolvers(
        uniresolverUrl: String? = null,
    ) = setOf(
        LocalResolver(),
        UniresolverResolver(uniresolverUrl ?: UniresolverResolver.DEFAULT_RESOLVER_URL)
    )

}

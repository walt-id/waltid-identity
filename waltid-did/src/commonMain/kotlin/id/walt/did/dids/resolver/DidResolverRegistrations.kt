package id.walt.did.dids.resolver

object DidResolverRegistrations {

    val didResolvers = setOf(
        LocalResolver(),
        UniresolverResolver()
    )

}

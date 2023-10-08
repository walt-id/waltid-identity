package id.walt.ssikit.did.resolver

object DidResolverRegistrations {

    val didResolvers = setOf(
        LocalResolver(),
        UniresolverResolver()
    )

}

package id.walt.didlib.did.resolver

object DidResolverRegistrations {

    val didResolvers = setOf(
        LocalResolver(),
        UniresolverResolver()
    )

}

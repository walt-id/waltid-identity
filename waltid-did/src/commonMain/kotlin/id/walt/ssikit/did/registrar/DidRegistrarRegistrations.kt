package id.walt.ssikit.did.registrar

object DidRegistrarRegistrations {

    val didRegistrars = setOf(
        LocalRegistrar(),
        UniregistrarRegistrar()
    )

}

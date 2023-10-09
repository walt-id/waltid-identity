package id.walt.did.dids.registrar

object DidRegistrarRegistrations {

    val didRegistrars = setOf(
        LocalRegistrar(),
        UniregistrarRegistrar()
    )

}

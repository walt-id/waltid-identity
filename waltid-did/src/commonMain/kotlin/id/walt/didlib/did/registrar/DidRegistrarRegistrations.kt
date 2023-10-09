package id.walt.didlib.did.registrar

object DidRegistrarRegistrations {

    val didRegistrars = setOf(
        LocalRegistrar(),
        UniregistrarRegistrar()
    )

}

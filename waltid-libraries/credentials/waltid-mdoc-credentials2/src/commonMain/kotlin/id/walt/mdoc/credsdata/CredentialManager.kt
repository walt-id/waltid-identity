package id.walt.mdoc.credsdata

object CredentialManager {


    val credentials: List<MdocCompanion> = listOf(
        Mdl, PhotoId, BoardingPass, BookingOrder
    )

    fun init() {
        if (hasInit) return

        credentials.forEach {
            it.registerSerializationTypes()
        }

        hasInit = true
    }

    var hasInit = false
}

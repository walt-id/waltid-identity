package id.walt.mdoc.credsdata

import id.walt.mdoc.credsdata.Mdl

object CredentialManager {


    val credentials: List<MdocCompanion> = listOf(
        Mdl, PhotoId
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

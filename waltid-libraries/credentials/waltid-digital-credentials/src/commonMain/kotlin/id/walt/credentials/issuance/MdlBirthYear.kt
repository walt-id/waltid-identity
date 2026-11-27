package id.walt.credentials.issuance

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The origin of an mDL birth year retained from offer creation through signing. */
@Serializable
sealed interface MdlBirthYear {
    @Serializable
    data object Inherited : MdlBirthYear

    @Serializable
    data class Explicit(val value: JsonElement) : MdlBirthYear
}

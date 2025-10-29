package id.walt.ktorauthnz.methods.initalauth

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.config.AuthMethodConfiguration
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class AuthMethodRegistration(
    val type: String,
    val identifier: AccountIdentifier,
    val data: AuthMethodStoredData? = null,
    val config: AuthMethodConfiguration? = null,
) {
    fun getAuthMethod() =
        data?.authMethod() ?: config?.authMethod() ?: error("Neither data nor config was provided for AuthMethodRegistration")
}

@Serializable
data class AuthMethodRegistrationWrapper(
    val type: String,
    var identifier: JsonObject, // AccountIdentifier

    var data: JsonObject? = null, // AuthMethodStoredData
    var config: JsonObject? = null, // AuthMethodConfiguration
) {

    companion object {
        fun parseWrapperFromJsonRequest(registrationJson: String) =
            Json.decodeFromString<AuthMethodRegistrationWrapper>(registrationJson)
    }

    /** Transform wrapper into actual data class */
    fun transformWrapperToRegistration(): AuthMethodRegistration {
        identifier = setInitialAuthJsonObjectType(identifier, type)

        if (data != null) {
            data = setInitialAuthJsonObjectType(data!!, type)
        }
        if (config != null) {
            data = setInitialAuthJsonObjectType(config!!, type)
        }
        return Json.decodeFromJsonElement<AuthMethodRegistration>(Json.encodeToJsonElement(this))
    }

}

fun setInitialAuthJsonObjectType(jsonObject: JsonObject, type: String): JsonObject =
    JsonObject(jsonObject.toMutableMap().apply { set("type", JsonPrimitive(type)) })

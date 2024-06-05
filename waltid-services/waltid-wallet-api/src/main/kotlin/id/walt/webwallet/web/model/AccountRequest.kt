package id.walt.webwallet.web.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
sealed class AccountRequest {
    abstract val name: String?
}

@Serializable
@SerialName("email")
data class EmailAccountRequest(
    override val name: String? = null,
    val email: String,
    val password: String
) : AccountRequest()

@Serializable
@SerialName("address")
data class AddressAccountRequest(
    override val name: String? = null,
    val address: String,
    val ecosystem: String
) : AccountRequest()

@Serializable
@SerialName("oidc")
data class OidcAccountRequest(override val name: String? = null, val token: String) :
    AccountRequest()

@Serializable
@SerialName("keycloak")
data class KeycloakAccountRequest(
    override val name: String? = null,
    val email: String? = null,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null
) : AccountRequest()

@Serializable
@SerialName("keycloak")
data class KeycloakLogoutRequest(val keycloakUserId: String? = null, val token: String? = null)

@Serializable
@SerialName("oidc-unique-subject")
data class OidcUniqueSubjectRequest(override val name: String? = null, val token: String) :
    AccountRequest()

val accountRequestSerializer = SerializersModule {
    polymorphic(AccountRequest::class) {
        EmailAccountRequest::class
        AddressAccountRequest::class
        OidcAccountRequest::class
        KeycloakAccountRequest::class
    }
}

val loginRequestJson = Json {
    serializersModule = accountRequestSerializer
    ignoreUnknownKeys = true
}

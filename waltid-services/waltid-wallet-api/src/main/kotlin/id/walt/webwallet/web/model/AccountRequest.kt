@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.webwallet.web.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
sealed class AccountRequest() {
    abstract val name: String?
}

@JsonIgnoreUnknownKeys
@Serializable
@SerialName("email")
data class EmailAccountRequest(
    override val name: String? = null,
    val email: String,
    val password: String,
) : AccountRequest()

@JsonIgnoreUnknownKeys
@Serializable
@SerialName("address")
data class AddressAccountRequest(
    override val name: String? = null,
    val address: String,
    val ecosystem: String,
) : AccountRequest()

@JsonIgnoreUnknownKeys
@Serializable
@SerialName("oidc")
data class OidcAccountRequest(
    override val name: String? = null,
    val token: String
) : AccountRequest()

@JsonIgnoreUnknownKeys
@Serializable
@SerialName("keycloak")
data class KeycloakAccountRequest(
    override val name: String? = null,
    val email: String? = null,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
) : AccountRequest()

@Serializable
data class KeycloakLogoutRequest(val keycloakUserId: String? = null, val token: String? = null)

@JsonIgnoreUnknownKeys
@Serializable
@SerialName("oidc-unique-subject")
data class OidcUniqueSubjectRequest(
    override val name: String? = null,
    val token: String
) : AccountRequest()

@JsonIgnoreUnknownKeys
@Serializable
@SerialName("x5c")
data class X5CAccountRequest(
    override val name: String? = null,
    val token: String,
) : AccountRequest()

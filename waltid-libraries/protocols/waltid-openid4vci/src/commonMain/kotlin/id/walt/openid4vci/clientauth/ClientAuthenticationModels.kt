package id.walt.openid4vci.clientauth

import id.walt.openid4vci.errors.OAuthError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthenticatedClient(
    val id: String,
    val authenticationMethod: String,
    val registered: Boolean = false,
    val confirmationJwk: JsonObject? = null,
    val claims: JsonObject? = null,
)

data class ClientAuthenticationContext(
    val authorizationServerIssuer: String? = null,
    val challenge: String? = null,
)

enum class ClientAuthenticationEndpoint {
    PUSHED_AUTHORIZATION,
    TOKEN,
}

sealed interface ClientAuthenticationResult {
    data object Unauthenticated : ClientAuthenticationResult
    data class Authenticated(val client: AuthenticatedClient) : ClientAuthenticationResult
    data class Failure(val error: OAuthError) : ClientAuthenticationResult
}

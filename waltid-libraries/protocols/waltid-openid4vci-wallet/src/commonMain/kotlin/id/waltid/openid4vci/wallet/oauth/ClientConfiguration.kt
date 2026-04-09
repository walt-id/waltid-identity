package id.waltid.openid4vci.wallet.oauth

import kotlinx.serialization.Serializable

/**
 * OAuth 2.0 client configuration for the wallet.
 * Contains the client identifier and redirect URIs used in authorization flows.
 *
 * @property clientId The OAuth 2.0 client identifier
 * @property redirectUris List of allowed redirect URIs for authorization responses
 */
@Serializable
data class ClientConfiguration(
    val clientId: String,
    val redirectUris: List<String>,
) {
    init {
        require(clientId.isNotBlank()) { "Client ID cannot be blank" }
        require(redirectUris.isNotEmpty()) { "At least one redirect URI must be provided" }
    }

    /**
     * Gets the primary redirect URI (first in the list)
     */
    val primaryRedirectUri: String
        get() = redirectUris.first()

    /**
     * Validates if a redirect URI is registered for this client
     */
    fun isValidRedirectUri(uri: String): Boolean =
        redirectUris.any { it.equals(uri, ignoreCase = true) }
}

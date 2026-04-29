package id.walt.openid4vci

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * For Testing Purposes
 * Simplified OAuth client contract.
 * Currently used only for storing client-related information.
 */
@Serializable
sealed interface Client {
    @SerialName("id")
    val id: String
    @SerialName("redirect_uris")
    val redirectUris: List<String>
    @SerialName("grant_types")
    val grantTypes: Set<String>
    @SerialName("response_types")
    val responseTypes: Set<String>
    @SerialName("scopes")
    val scopes: Set<String>
    @SerialName("audience")
    val audience: Set<String>
}

@Serializable
data class DefaultClient(
    @SerialName("id")
    override val id: String,
    @SerialName("redirect_uris")
    override val redirectUris: List<String>,
    @SerialName("grant_types")
    override val grantTypes: Set<String>,
    @SerialName("response_types")
    override val responseTypes: Set<String>,
    @SerialName("scopes")
    override val scopes: Set<String> = emptySet(),
    @SerialName("audience")
    override val audience: Set<String> = emptySet(),
) : Client

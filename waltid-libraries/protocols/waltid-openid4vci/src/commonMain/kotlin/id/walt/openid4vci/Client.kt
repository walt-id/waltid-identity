package id.walt.openid4vci

/**
 * For Testing Purposes
 * Simplified OAuth client contract.
 * Currently used only for storing client-related information.
 */
interface Client {
    val id: String
    val redirectUris: List<String>
    val grantTypes: Set<String>
    val responseTypes: Set<String>
    val scopes: Set<String>
    val audience: Set<String>
}

data class DefaultClient(
    override val id: String,
    override val redirectUris: List<String>,
    override val grantTypes: Set<String>,
    override val responseTypes: Set<String>,
    override val scopes: Set<String> = emptySet(),
    override val audience: Set<String> = emptySet(),
) : Client

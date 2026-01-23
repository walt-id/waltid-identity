package id.walt.openid4vci

/**
 * For Testing Purposes
 * Simplified OAuth client contract.
 * Currently used only for storing client-related information.
 */
interface Client {
    val id: String
    val redirectUris: List<String>
    val grantTypes: Arguments
    val responseTypes: Arguments
    val scopes: Arguments
    val audience: Arguments
}

data class DefaultClient(
    override val id: String = "",
    override val redirectUris: List<String> = emptyList(),
    override val grantTypes: Arguments = argumentsOf("authorization_code"),
    override val responseTypes: Arguments = argumentsOf("code"),
    override val scopes: Arguments = newArguments(),
    override val audience: Arguments = newArguments(),
) : Client

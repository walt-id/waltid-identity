package id.walt.wallet2.mobile.issuance

/**
 * OAuth registration used by the app when accepting a browser grant.
 * @property clientId Client identifier registered with the authorization server.
 * @property redirectUri Exact callback URI registered for browser authorization.
 */
public data class IssuanceClientRegistration(public val clientId: String, public val redirectUri: String) {
    init {
        require(clientId.isNotBlank()) { "Issuance client ID must not be blank" }
        require(redirectUri.isNotBlank() && redirectUri == redirectUri.trim()) { "Invalid registered redirect URI" }
    }
}

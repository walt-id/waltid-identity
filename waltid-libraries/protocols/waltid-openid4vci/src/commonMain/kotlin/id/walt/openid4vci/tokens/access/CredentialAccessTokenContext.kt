package id.walt.openid4vci.tokens.access

/**
 * Access-token presentation and trusted verification expectations for a protected OpenID4VCI endpoint.
 * [targetUri] must come from server configuration, not client-controlled forwarding headers.
 */
data class CredentialAccessTokenContext(
    val authorization: AccessTokenAuthorization,
    val expectedIssuer: String,
    val expectedAudience: String? = null,
    val dpopProofHeaderValues: List<String> = emptyList(),
    val targetUri: String? = null,
) {
    @Deprecated("Use targetUri", ReplaceWith("targetUri"))
    val credentialEndpointUri: String? get() = targetUri
}

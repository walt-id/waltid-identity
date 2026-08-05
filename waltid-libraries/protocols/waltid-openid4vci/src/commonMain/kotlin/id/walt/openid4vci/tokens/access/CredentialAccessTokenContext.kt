package id.walt.openid4vci.tokens.access

/**
 * Access-token presentation and trusted verification expectations for the Credential Endpoint.
 * [credentialEndpointUri] must come from server configuration, not client-controlled forwarding headers.
 */
data class CredentialAccessTokenContext(
    val authorization: AccessTokenAuthorization,
    val expectedIssuer: String,
    val expectedAudience: String? = null,
    val dpopProofHeaderValues: List<String> = emptyList(),
    val credentialEndpointUri: String? = null,
)

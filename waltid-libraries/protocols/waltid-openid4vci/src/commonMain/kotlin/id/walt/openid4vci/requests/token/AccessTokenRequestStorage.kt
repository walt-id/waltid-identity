package id.walt.openid4vci.requests.token

private val DEFAULT_STORED_REQUEST_PARAMETERS = setOf(
    "grant_type",
    "response_type",
    "scope",
    "client_id",
)

internal fun AccessTokenRequest.sanitizeForStorage(
    allowedParameters: Set<String> = emptySet(),
): AccessTokenRequest {
    val allowed = DEFAULT_STORED_REQUEST_PARAMETERS + allowedParameters
    return DefaultAccessTokenRequest(
        id = id,
        requestedAt = requestedAt,
        client = client,
        grantTypes = grantTypes,
        handledGrantTypes = handledGrantTypes,
        requestedScopes = requestedScopes,
        grantedScopes = grantedScopes,
        requestedAudience = requestedAudience,
        grantedAudience = grantedAudience,
        requestForm = requestForm
            .filterKeys { it in allowed }
            .mapValues { (_, values) -> values.toList() },
        session = session,
        issClaim = issClaim,
    )
}

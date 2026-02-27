package id.walt.openid4vci.requests.authorization

import id.walt.openid4vci.Client
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.requests.generateRequestId
import kotlin.time.Clock
import kotlin.time.Instant

data class DefaultAuthorizationRequest(
    override val id: String = generateRequestId(),
    override val requestedAt: Instant = Clock.System.now(),
    override val client: Client,
    override val responseTypes: Set<String>,
    override val handledResponseTypes: Set<String> = emptySet(),
    override val requestedScopes: Set<String> = emptySet(),
    override val grantedScopes: Set<String> = emptySet(),
    override val requestedAudience: Set<String> = emptySet(),
    override val grantedAudience: Set<String> = emptySet(),
    override val redirectUri: String?,
    override val state: String?,
    override val issuerState: String? = null,
    override val responseMode: ResponseMode = ResponseMode.QUERY,
    override val defaultResponseMode: ResponseMode = ResponseMode.QUERY,
    override val requestForm: Map<String, List<String>> = emptyMap(),
    override val issClaim: String? = null,
) : AuthorizationRequest {
    override fun markResponseTypeHandled(responseType: String): AuthorizationRequest =
        copy(handledResponseTypes = handledResponseTypes + responseType)

    override fun grantScopes(scopes: Collection<String>): AuthorizationRequest =
        copy(grantedScopes = grantedScopes + scopes)

    override fun grantAudience(audience: Collection<String>): AuthorizationRequest =
        copy(grantedAudience = grantedAudience + audience)

    override fun withIssuer(issClaim: String?): AuthorizationRequest =
        copy(issClaim = issClaim)

    override fun withRedirectUri(uri: String?): AuthorizationRequest =
        copy(redirectUri = uri)
}

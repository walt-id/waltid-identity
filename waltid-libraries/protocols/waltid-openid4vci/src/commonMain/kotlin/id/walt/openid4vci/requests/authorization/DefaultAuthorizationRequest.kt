package id.walt.openid4vci.requests.authorization

import id.walt.openid4vci.Client
import id.walt.openid4vci.ResponseMode
import id.walt.openid4vci.clientauth.AuthenticatedClient
import id.walt.openid4vci.requests.generateRequestId
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
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
    override val authorizationDetails: List<AuthorizationDetail> = emptyList(),
    override val authenticatedClient: AuthenticatedClient? = null,
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

    override fun withAuthenticatedClient(authenticatedClient: AuthenticatedClient?): AuthorizationRequest =
        copy(authenticatedClient = authenticatedClient)
}

/** Snapshot the declared request fields for persistence across authentication. */
fun AuthorizationRequest.toDefaultAuthorizationRequest(): DefaultAuthorizationRequest =
    if (this is DefaultAuthorizationRequest) this else DefaultAuthorizationRequest(
        id = id,
        requestedAt = requestedAt,
        client = client,
        responseTypes = responseTypes,
        handledResponseTypes = handledResponseTypes,
        requestedScopes = requestedScopes,
        grantedScopes = grantedScopes,
        requestedAudience = requestedAudience,
        grantedAudience = grantedAudience,
        redirectUri = redirectUri,
        state = state,
        issuerState = issuerState,
        responseMode = responseMode,
        defaultResponseMode = defaultResponseMode,
        requestForm = requestForm,
        issClaim = issClaim,
        authorizationDetails = authorizationDetails,
        authenticatedClient = authenticatedClient,
    )

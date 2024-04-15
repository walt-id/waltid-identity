package id.walt.oid4vc

import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.http
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object OpenID4VP {

    /**
     * Create a OpenID4VP presentation request object
     * @param presentationDefinition Presentation definition either by value, by reference or scope
     * @param responseMode Response mode, defaults to direct_post
     * @param responseTypes Expected response types, defaults to "vp_token", use "code" for authorization code flow, or "vp_token id_token" for SIOPv2 flow with "openid" scope
     * @param redirectOrResponseUri Sets either the "redirect_uri" or "response_uri" parameter on the request, depending on the response mode used.
     * @param nonce Used to securely bind the Verifiable Presentation provided by the wallet to the particular transaction
     * @param state State property that can be used to identify the presentation session when receiving the response from the wallet
     * @param scopes Can be used to set additional scopes on the request, e.g. to request presentations based on a pre-defined scope (see also presentationDefinition param), default: empty
     * @param clientId Public identifier of the service
     * @param clientIdScheme Scheme of the client_id
     * @param clientMetadataParameter client metadata, that can be passed by value or by reference (optional)
     * @return Presentation request
     */
    fun createPresentationRequest(
        presentationDefinition: PresentationDefinitionParameter,
        responseMode: ResponseMode = ResponseMode.direct_post,
        responseTypes: Set<ResponseType> = setOf(ResponseType.VpToken),
        redirectOrResponseUri: String?,
        nonce: String?,
        state: String?,
        scopes: Set<String> = setOf(),
        clientId: String,
        clientIdScheme: ClientIdScheme?,
        clientMetadataParameter: ClientMetadataParameter?,
    ): AuthorizationRequest {
        return AuthorizationRequest(
            responseType = responseTypes,
            clientId = clientId,
            responseMode = responseMode,
            redirectUri = when (responseMode) {
                ResponseMode.direct_post -> null
                else -> redirectOrResponseUri
            },
            responseUri = when (responseMode) {
                ResponseMode.direct_post -> redirectOrResponseUri
                else -> null
            },
            presentationDefinition = presentationDefinition.presentationDefinition,
            presentationDefinitionUri = presentationDefinition.presentationDefinitionUri,
            scope = presentationDefinition.presentationDefinitionScope?.let { setOf(it).plus(scopes) } ?: scopes,
            state = state,
            nonce = nonce,
            clientIdScheme = clientIdScheme,
            clientMetadata = clientMetadataParameter?.clientMetadata,
            clientMetadataUri = clientMetadataParameter?.clientMetadataUri
        )
    }

    /**
     * Generates the authorization URL for the given presentation request, which can be rendered as a QR code (cross-device flow),
     * or called on the authorization endpoint of the wallet (same-device flow).
     * @param presentationRequest Presentation request to be rendered
     * @param authorizationEndpoint Authorization endpoint of the wallet (same-device flow), default: "openid4vp://authorize" (cross-device flow)
     */
    fun getAuthorizationUrl(presentationRequest: AuthorizationRequest, authorizationEndpoint: String = "openid4vp://authorize") =
        "$authorizationEndpoint?${presentationRequest.toHttpQueryString()}"

    /**
     * Parses a presentation request Url, and resolves parameter objects given by reference if necessary
     * @param url Presentation request Url
     * @return Presentation request
     */
    suspend fun parsePresentationRequestFromUrl(url: String): AuthorizationRequest = AuthorizationRequest.fromHttpParametersAuto(
        Url(url).parameters.toMap()
    )

    // TODO: Extract flow details (implicit/code flow, same-device/cross-device) if necessary/possible

    /**
     * Get or resolve presentation definition from authorization request.
     * Tries to fetch and parse the presentation definition from the given http URL, if the presentation_definition_uri parameter is set.
     * @param authorizationRequest The presentation request
     * @param scopeMapping Optional lambda function to resolve presentation definition from a scope value (see section 5.3 of https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
     * @return The resolved presentation definition
     * @throws AuthorizationError If no presentation definition can be found on the given request either by value or by reference
     */
    suspend fun resolvePresentationDefinition(
        authorizationRequest: AuthorizationRequest,
        scopeMapping: ((String) -> PresentationDefinition?)? = null
    ): PresentationDefinition =
        authorizationRequest.presentationDefinition ?: authorizationRequest.presentationDefinitionUri?.let { uri ->
            http.get(uri).bodyAsText().let { PresentationDefinition.fromJSONString(it) }
        } ?: scopeMapping?.let { authorizationRequest.scope.firstNotNullOfOrNull(it) } ?: throw AuthorizationError(
            authorizationRequest,
            AuthorizationErrorCode.invalid_request,
            "No presentation definition found on given presentation request"
        )

    /**
     * Generates a presentation token response, for the given presentation result
     * @param presentationResult The presentation result containing the presentation and presentation submission
     * @param state Optional state parameter from the presentation request
     * @param code Optional code parameter
     * @param idToken  Optional id_token parameter, for combination with SIOPv2
     * @param iss Optional issuer parameter from RFC9207
     * @return The token response
     */
    fun generatePresentationResponse(
        presentationResult: PresentationResult,
        state: String? = null,
        code: String? = null,
        idToken: String? = null,
        iss: String? = null
    ): TokenResponse {
        return if (presentationResult.presentations.size == 1) {
            TokenResponse.success(
                VpTokenParameter.fromJsonElement(presentationResult.presentations.first()),
                presentationResult.presentationSubmission,
                state = state,
                idToken = idToken
            )
        } else {
            TokenResponse.success(
                VpTokenParameter.fromJsonElement(JsonArray(presentationResult.presentations)),
                presentationResult.presentationSubmission,
                state = state,
                idToken = idToken
            )
        }.copy(customParameters = buildMap {
            code?.let { put("code", JsonPrimitive(it)) }
            iss?.let { put("iss", JsonPrimitive(it)) }
        })
    }

    /**
     * Parse token response URI (redirect_url) and generate token response object.
     * @param url Response or redirect url
     * @return Token response object
     */
    fun parsePresentationResponseFromUrl(url: String): TokenResponse = TokenResponse.fromHttpParameters(
        Url(url).parameters.toMap()
    )
}

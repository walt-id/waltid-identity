package id.walt.oid4vc

import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest

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
   *
   */
  fun createPresentationRequest(
    presentationDefinition: PresentationDefinitionParameter,
    responseMode: ResponseMode = ResponseMode.DirectPost,
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
        ResponseMode.DirectPost -> null
        else -> redirectOrResponseUri
      },
      responseUri = when (responseMode) {
        ResponseMode.DirectPost -> redirectOrResponseUri
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
  fun getAuthorizationUrl(presentationRequest: AuthorizationRequest, authorizationEndpoint: String = "openid4vp://authorize") = "$authorizationEndpoint?${presentationRequest.toHttpQueryString()}"

}
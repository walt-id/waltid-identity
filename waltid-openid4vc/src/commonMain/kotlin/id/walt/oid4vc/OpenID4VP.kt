package id.walt.oid4vc

import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest

object OpenID4VP {

  fun createPresentationRequest(
    presentationDefinition: PresentationDefinitionParameter,
    responseMode: ResponseMode,
    responseTypes: Set<ResponseType>,
    redirectOrResponseUri: String?,
    nonce: String?,
    state: String?,
    accessTokenScopes: Set<String>,
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
      scope = presentationDefinition.presentationDefinitionScope?.let { setOf(it).plus(accessTokenScopes) } ?: accessTokenScopes,
      state = state,
      nonce = nonce,
      clientIdScheme = clientIdScheme,
      clientMetadata = clientMetadataParameter?.clientMetadata,
      clientMetadataUri = clientMetadataParameter?.clientMetadataUri
    )
  }
}
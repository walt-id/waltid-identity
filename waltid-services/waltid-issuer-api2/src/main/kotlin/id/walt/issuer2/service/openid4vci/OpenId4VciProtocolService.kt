package id.walt.issuer2.service.openid4vci

import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenId4VciProtocolService(
    private val oauth2Provider: OAuth2Provider,
    private val sessionService: IssuanceSessionService,
    private val metadataService: MetadataService,
) {
    suspend fun processAuthorizeRequest(parameters: Map<String, List<String>>): AuthorizationResponseHttp {
        val authorizationRequest = when (val result = oauth2Provider.createAuthorizationRequest(parameters)) {
            is AuthorizationRequestResult.Success -> result.request
            is AuthorizationRequestResult.Failure -> throw IllegalArgumentException(
                result.error.description ?: result.error.error
            )
        }

        val sessionId = authorizationRequest.issuerState
            ?: throw IllegalArgumentException("issuer_state is required for OSS issuer2 authorization flow")
        val issuanceSession = sessionService.getSession(sessionId)

        val requestWithIssuer = authorizationRequest.withIssuer(metadataService.issuerBaseUrl())
        val oauthSession = DefaultSession(subject = issuanceSession.sessionId)
        val authorizationResponse = when (
            val result = oauth2Provider.createAuthorizationResponse(requestWithIssuer, oauthSession)
        ) {
            is AuthorizationResponseResult.Success -> result.response
            is AuthorizationResponseResult.Failure -> throw IllegalArgumentException(
                result.error.description ?: result.error.error
            )
        }

        sessionService.createSession(
            issuanceSession.copy(authorizationRequest = parameters)
        )

        return oauth2Provider.writeAuthorizationResponse(requestWithIssuer, authorizationResponse)
    }

    suspend fun processTokenRequest(parameters: Map<String, List<String>>): AccessTokenResponseHttp {
        val accessTokenRequest = when (val result = oauth2Provider.createAccessTokenRequest(parameters)) {
            is AccessTokenRequestResult.Success -> result.request
            is AccessTokenRequestResult.Failure -> throw IllegalArgumentException(result.error.description ?: result.error.error)
        }

        val response = when (val result = oauth2Provider.createAccessTokenResponse(accessTokenRequest)) {
            is AccessTokenResponseResult.Success -> {
                result.request.session?.subject?.let { sessionId ->
                    sessionService.updateStatus(sessionId, IssuanceSessionStatus.TOKEN_REQUESTED)
                }
                result.response
            }

            is AccessTokenResponseResult.Failure -> {
                return oauth2Provider.writeAccessTokenError(accessTokenRequest, result.error)
            }
        }

        return oauth2Provider.writeAccessTokenResponse(accessTokenRequest, response)
    }

    fun processCredentialRequest(accessToken: String, parameters: JsonObject): JsonObject =
        buildJsonObject {
            put("status", "not_implemented")
            put("message", "Credential issuance endpoint scaffolded; signing flow will be wired next.")
            put("accessTokenPresent", accessToken.isNotBlank())
            put("request", parameters)
        }

    fun createNonceResponse(): Map<String, JsonPrimitive> =
        mapOf("c_nonce" to JsonPrimitive(java.util.UUID.randomUUID().toString()))
}

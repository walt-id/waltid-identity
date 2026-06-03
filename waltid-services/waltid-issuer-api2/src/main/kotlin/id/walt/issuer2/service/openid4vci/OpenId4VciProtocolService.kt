package id.walt.issuer2.service.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.utils.JsonObjectPathMapper
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.tokens.AccessTokenContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class OpenId4VciProtocolService(
    private val oauth2Provider: OAuth2Provider,
    private val sessionService: IssuanceSessionService,
    private val profileService: CredentialProfileService,
    private val metadataService: MetadataService,
    private val externalOAuthProviderClient: ExternalOAuthProviderClient,
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
        require(issuanceSession.authenticationMethod == AuthenticationMethod.AUTHORIZED) {
            "Issuance session $sessionId is not configured for authorization-code flow"
        }

        if (externalOAuthProviderClient.enabled) {
            val externalState = UUID.randomUUID().toString()
            sessionService.saveSession(
                issuanceSession.copy(
                    authorizationRequest = parameters,
                    externalAuthorizationState = externalState,
                )
            )
            val redirectUri = externalOAuthProviderClient.buildAuthorizationRedirect(externalState)
            return AuthorizationResponseHttp(
                status = 302,
                redirectUri = redirectUri,
                headers = mapOf("Location" to redirectUri),
            )
        }

        return createAuthorizationResponse(parameters, claims = null)
    }

    suspend fun processExternalAuthorizationCallback(parameters: Map<String, List<String>>): AuthorizationResponseHttp {
        val state = parameters["state"]?.singleOrNull()
            ?: throw IllegalArgumentException("External OAuth callback is missing state")
        val code = parameters["code"]?.singleOrNull()
            ?: throw IllegalArgumentException("External OAuth callback is missing code")

        val session = sessionService.findByExternalAuthorizationState(state)
            ?: throw IllegalArgumentException("No issuance session found for external OAuth state")
        val authorizationRequestParameters = session.authorizationRequest
            ?: throw IllegalArgumentException("Issuance session ${session.sessionId} has no stored authorization request")

        val tokenResult = externalOAuthProviderClient.redeemAuthorizationCode(code)
        val credentialData = session.idTokenClaimsMapping?.let { claimsMapping ->
            JsonObjectPathMapper.fromSourceToDestinationJsonPathsMap(
                source = tokenResult.idTokenClaims,
                destination = session.credentialData,
                jsonPathMapConfig = claimsMapping,
            )
        } ?: session.credentialData
        sessionService.saveSession(
            session.copy(
                credentialData = credentialData,
                authorizationClaims = tokenResult.idTokenClaims,
                externalAuthorizationState = null,
            )
        )

        return createAuthorizationResponse(authorizationRequestParameters, claims = tokenResult.idTokenClaims)
    }

    suspend fun processTokenRequest(parameters: Map<String, List<String>>): AccessTokenResponseHttp {
        val accessTokenRequest = when (val result = oauth2Provider.createAccessTokenRequest(parameters)) {
            is AccessTokenRequestResult.Success -> result.request
            is AccessTokenRequestResult.Failure -> return oauth2Provider.writeAccessTokenError(result.error)
        }.withIssuer(metadataService.issuerBaseUrl())

        val (updatedAccessTokenRequest, response) = when (val result = oauth2Provider.createAccessTokenResponse(accessTokenRequest)) {
            is AccessTokenResponseResult.Success -> result.request to result.response

            is AccessTokenResponseResult.Failure -> {
                return oauth2Provider.writeAccessTokenError(accessTokenRequest, result.error)
            }
        }

        val sessionId = updatedAccessTokenRequest.session?.subject
            ?: return oauth2Provider.writeAccessTokenError(
                updatedAccessTokenRequest,
                OAuthError("invalid_request", "No session subject found"),
            )
        sessionService.updateStatus(sessionId, IssuanceSessionStatus.TOKEN_REQUESTED)

        return oauth2Provider.writeAccessTokenResponse(updatedAccessTokenRequest, response)
    }

    suspend fun processCredentialRequest(accessToken: String, parameters: JsonObject): CredentialResponseHttp {
        val parameterMap = parameters.toParametersMap()
        val credentialRequest = when (
            val result = oauth2Provider.createCredentialRequest(
                parameters = parameterMap,
                accessTokenContext = AccessTokenContext(
                    token = accessToken,
                    expectedIssuer = metadataService.issuerBaseUrl(),
                ),
            )
        ) {
            is CredentialRequestResult.Success -> result.request
            is CredentialRequestResult.Failure -> return CredentialResponseHttp(
                status = 400,
                payload = buildMap {
                    put("error", JsonPrimitive(result.error.error))
                    result.error.description?.let { put("error_description", JsonPrimitive(it)) }
                },
            )
        }
        val tokenClaims = accessToken.decodeJws().payload

        val credentialConfigurationId = credentialRequest.credentialConfigurationId
            ?: credentialRequest.credentialIdentifier
            ?: return credentialError("credential_configuration_id or credential_identifier is required")
        val sessionId = resolveSessionId(tokenClaims)
        val session = sessionService.getSession(sessionId)
        require(session.credentialConfigurationId == credentialConfigurationId) {
            "Credential request references $credentialConfigurationId, but session ${session.sessionId} is for ${session.credentialConfigurationId}"
        }

        val configuration = metadataService.getCredentialConfiguration(credentialConfigurationId)
            ?: return credentialError("Unsupported credential_configuration_id: $credentialConfigurationId")
        val issuerKey = KeyManager.resolveSerializedKey(profileService.resolveProfile(session.profileId).issuerKey)
        val issuerId = session.issuerDid ?: metadataService.issuerBaseUrl()
        val requestWithSession = credentialRequest
            .withSession(DefaultSession(subject = sessionId))
            .withIssuer(issuerId)

        val credentialResponse = when (
            val result = oauth2Provider.createCredentialResponse(
                request = requestWithSession,
                configuration = configuration,
                issuerKey = issuerKey,
                issuerId = issuerId,
                credentialData = session.credentialData,
                dataMapping = session.mapping,
                selectiveDisclosure = session.selectiveDisclosure,
                x5Chain = session.x5Chain?.map { id.walt.x509.CertificateDer.fromPEMEncodedString(it) },
                mDocNameSpacesDataMappingConfig = session.mDocNameSpacesDataMappingConfig,
            )
        ) {
            is CredentialResponseResult.Success -> result.response
            is CredentialResponseResult.Failure -> {
                sessionService.updateStatus(
                    session.sessionId,
                    IssuanceSessionStatus.UNSUCCESSFUL,
                    result.error.description ?: result.error.error,
                )
                return oauth2Provider.writeCredentialError(requestWithSession, result.error)
            }
        }

        sessionService.updateStatus(
            session.sessionId,
            IssuanceSessionStatus.SUCCESSFUL,
            "Credential issued successfully",
            issuedCredentialFormat = configuration.format.value,
        )

        return oauth2Provider.writeCredentialResponse(requestWithSession, credentialResponse)
    }

    fun createNonceResponse(): Map<String, String> =
        mapOf("c_nonce" to UUID.randomUUID().toString())

    private suspend fun createAuthorizationResponse(
        parameters: Map<String, List<String>>,
        claims: JsonObject?,
    ): AuthorizationResponseHttp {
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

        sessionService.saveSession(
            issuanceSession.copy(
                authorizationRequest = parameters,
                authorizationClaims = claims ?: issuanceSession.authorizationClaims,
                externalAuthorizationState = null,
            )
        )

        return oauth2Provider.writeAuthorizationResponse(requestWithIssuer, authorizationResponse)
    }

    private fun JsonObject.toParametersMap(): Map<String, List<String>> =
        entries.associate { (key, value) ->
            key to listOf(if (value is JsonPrimitive && value.isString) {
                value.content
            } else {
                value.toString()
            })
        }

    private fun resolveSessionId(tokenClaims: JsonObject): String =
        tokenClaims.stringClaim("sub")
            ?: throw IllegalArgumentException("Access token has no session id")

    private fun JsonObject.stringClaim(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun credentialError(description: String): CredentialResponseHttp =
        CredentialResponseHttp(
            status = 400,
            payload = mapOf(
                "error" to JsonPrimitive("invalid_request"),
                "error_description" to JsonPrimitive(description),
            ),
        )
}

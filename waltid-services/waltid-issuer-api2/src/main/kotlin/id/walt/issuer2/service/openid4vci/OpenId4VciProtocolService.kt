package id.walt.issuer2.service.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.utils.JsonObjectPathMapper
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.AccessTokenContext
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseQueryString
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER = "_issuer2_session_id"
private val AUTHORIZATION_CODE_SESSION_LIFETIME = 5.minutes

class OpenId4VciProtocolService(
    private val oauth2Provider: OAuth2Provider,
    private val sessionService: IssuanceSessionService,
    private val profileService: CredentialProfileService,
    private val metadataService: MetadataService,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun processAuthorizeRequest(parameters: Map<String, List<String>>): AuthorizationResponseHttp {
        val authorizationRequest = when (val result = oauth2Provider.createAuthorizationRequest(parameters)) {
            is AuthorizationRequestResult.Success -> result.request
            is AuthorizationRequestResult.Failure -> return oauth2Provider.writeAuthorizationError(result.error)
        }

        val issuanceSession = try {
            resolveAuthorizationSession(authorizationRequest, parameters)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return oauth2Provider.writeAuthorizationError(authorizationRequest, e.toAuthorizationError())
        }
        val internalAuthorizationRequest = parameters.withInternalAuthorizationSession(issuanceSession.sessionId)

        val redirectUri = "${metadataService.issuerBaseUrl()}/external_login/${internalAuthorizationRequest.toQueryString()}"
        return AuthorizationResponseHttp(
            status = 302,
            redirectUri = redirectUri,
            headers = mapOf("Location" to redirectUri),
        )
    }

    suspend fun processExternalLoginInterception(
        externalAuthorizationRequest: String?,
        internalAuthorizationRequest: String?,
    ) {
        val externalState = externalAuthorizationRequest
            ?.substringAfter("?", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.let { parseQueryParameters(it)["state"]?.singleOrNull() }
            ?: throw IllegalArgumentException("Missing state in external authorization request")

        val authorizationRequestParameters = internalAuthorizationRequest
            ?.takeIf { it.isNotBlank() }
            ?.let { parseQueryParameters(it) }
            ?: throw IllegalArgumentException("Missing internal authorization request")

        val sessionId = authorizationRequestParameters[INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER]?.singleOrNull()
            ?: authorizationRequestParameters["issuer_state"]?.singleOrNull()
            ?: throw IllegalArgumentException("Missing issuance session id in internal authorization request")
        val session = sessionService.getSession(sessionId)
        sessionService.saveSession(
            session.copy(
                authorizationRequest = authorizationRequestParameters.withoutInternalAuthorizationSession(),
                externalAuthorizationState = externalState,
            )
        )
    }

    suspend fun processExternalAuthorizationCallback(
        authServerState: String,
        idToken: String,
    ): AuthorizationResponseHttp {
        val session = sessionService.findByExternalAuthorizationState(authServerState)
            ?: return oauth2Provider.writeAuthorizationError(
                OAuthError(OAuthErrorCodes.INVALID_REQUEST, "No issuance session found for external OAuth state")
            )
        val authorizationRequestParameters = session.authorizationRequest
            ?: return oauth2Provider.writeAuthorizationError(
                OAuthError(
                    OAuthErrorCodes.INVALID_REQUEST,
                    "Issuance session ${session.sessionId} has no stored authorization request",
                )
            )

        val authorizationRequest = when (
            val result = oauth2Provider.createAuthorizationRequest(authorizationRequestParameters)
        ) {
            is AuthorizationRequestResult.Success -> result.request
            is AuthorizationRequestResult.Failure -> return oauth2Provider.writeAuthorizationError(result.error)
        }

        val idTokenClaims = try {
            idToken.decodeJws().payload
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return oauth2Provider.writeAuthorizationError(authorizationRequest, e.toAuthorizationError())
        }
        val credentialData = try {
            session.idTokenClaimsMapping?.let { claimsMapping ->
                JsonObjectPathMapper.fromSourceToDestinationJsonPathsMap(
                    source = idTokenClaims,
                    destination = session.credentialData,
                    jsonPathMapConfig = claimsMapping,
                )
            } ?: session.credentialData
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return oauth2Provider.writeAuthorizationError(authorizationRequest, e.toAuthorizationError())
        }
        val updatedSession = session.copy(
            credentialData = credentialData,
            authorizationClaims = idTokenClaims,
            externalAuthorizationState = null,
        )
        sessionService.saveSession(updatedSession)

        return createAuthorizationResponse(
            issuanceSession = updatedSession,
            authorizationRequest = authorizationRequest,
            parameters = authorizationRequestParameters,
            claims = idTokenClaims,
        )
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
            is CredentialRequestResult.Failure -> return oauth2Provider.writeCredentialError(result.error)
        }
        val tokenClaims = accessToken.decodeJws().payload

        val credentialConfigurationId = credentialRequest.credentialConfigurationId
            ?: credentialRequest.credentialIdentifier
            ?: return oauth2Provider.writeCredentialError(
                credentialRequest,
                OAuthError(
                    OAuthErrorCodes.INVALID_REQUEST,
                    "credential_configuration_id or credential_identifier is required",
                ),
            )
        val sessionId = resolveSessionId(tokenClaims)
        val session = sessionService.getSession(sessionId)
        require(session.credentialConfigurationId == credentialConfigurationId) {
            "Credential request references $credentialConfigurationId, but session ${session.sessionId} is for ${session.credentialConfigurationId}"
        }

        val configuration = metadataService.getCredentialConfiguration(credentialConfigurationId)
            ?: return oauth2Provider.writeCredentialError(
                credentialRequest,
                OAuthError(
                    OAuthErrorCodes.INVALID_REQUEST,
                    "Unsupported credential_configuration_id: $credentialConfigurationId",
                ),
            )
        val issuerKey = KeyManager.resolveSerializedKey(session.issuerKey)
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
        issuanceSession: IssuanceSession,
        authorizationRequest: AuthorizationRequest,
        parameters: Map<String, List<String>>,
        claims: JsonObject?,
    ): AuthorizationResponseHttp {
        val requestWithIssuer = authorizationRequest.withIssuer(metadataService.issuerBaseUrl())
        val oauthSession = DefaultSession(subject = issuanceSession.sessionId)

        val authorizationResponse = when (
            val result = oauth2Provider.createAuthorizationResponse(requestWithIssuer, oauthSession)
        ) {
            is AuthorizationResponseResult.Success -> result.response
            is AuthorizationResponseResult.Failure -> return oauth2Provider.writeAuthorizationError(
                requestWithIssuer,
                result.error,
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

    private suspend fun resolveAuthorizationSession(
        authorizationRequest: AuthorizationRequest,
        parameters: Map<String, List<String>>,
    ): IssuanceSession =
        authorizationRequest.issuerState
            ?.let { sessionId ->
                sessionService.getSession(sessionId).also { session ->
                    require(session.authenticationMethod == AuthenticationMethod.AUTHORIZED) {
                        "Issuance session $sessionId is not configured for authorization-code flow"
                    }
                }
            }
            ?: createAuthorizationCodeSessionFromProfile(authorizationRequest, parameters)

    private suspend fun createAuthorizationCodeSessionFromProfile(
        authorizationRequest: AuthorizationRequest,
        parameters: Map<String, List<String>>,
    ): IssuanceSession {
        val credentialConfigurationId = resolveRequestedCredentialConfigurationId(authorizationRequest, parameters)
        val profile = profileService.resolveProfileByCredentialConfigurationId(credentialConfigurationId)
        return sessionService.createSession(profile.toAuthorizationCodeSession(parameters))
    }

    private fun CredentialProfile.toAuthorizationCodeSession(
        authorizationRequest: Map<String, List<String>>,
    ): IssuanceSession =
        IssuanceSession(
            sessionId = UUID.randomUUID().toString(),
            profileId = profileId,
            authenticationMethod = AuthenticationMethod.AUTHORIZED,
            credentialConfigurationId = credentialConfigurationId,
            issuerKey = issuerKey,
            credentialData = credentialData,
            mapping = mapping,
            selectiveDisclosure = selectiveDisclosure,
            idTokenClaimsMapping = idTokenClaimsMapping,
            mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
            x5Chain = x5Chain,
            issuerDid = issuerDid,
            authorizationRequest = authorizationRequest,
            expiresAt = Clock.System.now() + AUTHORIZATION_CODE_SESSION_LIFETIME,
            webhookUrl = webhookUrl,
        )

    private fun resolveRequestedCredentialConfigurationId(
        authorizationRequest: AuthorizationRequest,
        parameters: Map<String, List<String>>,
    ): String {
        val authorizationDetailsMatches = credentialConfigurationIdsFromAuthorizationDetails(parameters)
        val scopeMatches = metadataService.credentialConfigurationIdsForScopes(authorizationRequest.requestedScopes)
        val matches = authorizationDetailsMatches + scopeMatches

        require(matches.isNotEmpty()) {
            "No credential configuration could be resolved from authorization_details or requested scopes: " +
                authorizationRequest.requestedScopes
        }
        require(matches.size == 1) {
            "Ambiguous credential configuration for authorization request: $matches"
        }

        return matches.single()
    }

    private fun credentialConfigurationIdsFromAuthorizationDetails(
        parameters: Map<String, List<String>>,
    ): Set<String> =
        parameters["authorization_details"]
            .orEmpty()
            .flatMap { raw -> parseAuthorizationDetails(raw) }
            .mapNotNull { detail ->
                detail["credential_configuration_id"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
            }
            .toSet()

    private fun parseAuthorizationDetails(raw: String): List<JsonObject> {
        val element = runCatching { json.parseToJsonElement(raw) }
            .getOrElse { throw IllegalArgumentException("Invalid authorization_details JSON", it) }

        return when (element) {
            is JsonArray -> element.map { it.jsonObject }
            is JsonObject -> listOf(element)
            else -> throw IllegalArgumentException("authorization_details must be a JSON object or array")
        }
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

    private fun Map<String, List<String>>.withInternalAuthorizationSession(sessionId: String): Map<String, List<String>> =
        filterKeys { it != INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER } +
            (INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER to listOf(sessionId))

    private fun Map<String, List<String>>.withoutInternalAuthorizationSession(): Map<String, List<String>> =
        filterKeys { it != INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER }

    private fun Map<String, List<String>>.toQueryString(): String =
        entries.flatMap { (key, values) ->
            values.map { value ->
                "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
            }
        }.joinToString("&")

    private fun parseQueryParameters(query: String): Map<String, List<String>> =
        parseQueryString(query).entries().associate { it.key to it.value }

    private fun Exception.toAuthorizationError(): OAuthError =
        when (this) {
            is IllegalArgumentException,
            is NotFoundException -> OAuthError(OAuthErrorCodes.INVALID_REQUEST, message)

            else -> OAuthError(
                OAuthErrorCodes.SERVER_ERROR,
                message ?: "Authorization request processing failed",
            )
        }

}

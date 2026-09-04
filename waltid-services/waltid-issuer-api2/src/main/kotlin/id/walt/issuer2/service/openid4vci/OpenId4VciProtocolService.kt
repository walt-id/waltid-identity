package id.walt.issuer2.service.openid4vci

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionFailure
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.domain.IssuanceRequest
import id.walt.issuer2.domain.IssuanceResult
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.utils.JsonObjectPathMapper
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestTargetResolution
import id.walt.openid4vci.requests.credential.resolveCredentialConfigurationId
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.proofs.CredentialNonceBinding
import id.walt.openid4vci.proofs.CredentialNonceService
import id.walt.openid4vci.proofs.CredentialNonceValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.openid4vci.proofs.CredentialProofVerifier
import id.walt.openid4vci.proofs.DefaultCredentialProofVerifier
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.nonce.NonceResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.access.CredentialAccessTokenContext
import id.walt.openid4vci.tokens.access.parseAccessTokenAuthorization
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.mdoc.objects.mso.Status as MdocStatus
import id.walt.mdoc.objects.mso.Status.StatusListInfo as MdocStatusListInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.parseQueryString
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

private const val INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER = "_issuer2_session_id"
private const val TOKEN_ENDPOINT_PATH = "token"
private const val CREDENTIAL_ENDPOINT_PATH = "credential"
private val AUTHORIZATION_CODE_SESSION_LIFETIME = 5.minutes

internal suspend fun restoreSessionIssuerCrypto2Key(
    session: IssuanceSession,
    runtime: CryptoRuntime,
): Crypto2Key? {
    val encoded = session.crypto2IssuerStoredKey
    if (encoded == null) {
        require(session.issuerKey["type"]?.jsonPrimitive?.content != "jwk") {
            "JWK issuer session is missing its validated crypto2 key"
        }
        return null
    }
    return runtime.restore(StoredKeyCodec.decodeFromString(encoded))
}

class OpenId4VciProtocolService @JvmOverloads constructor(
    private val oauth2Provider: OAuth2Provider,
    private val sessionService: IssuanceSessionService,
    private val profileService: CredentialProfileService,
    private val metadataService: MetadataService,
    private val notificationService: IssuanceNotificationService,
    private val credentialNonceService: CredentialNonceService,
    /** Vetoes a credential proof key before the credential is constructed. */
    private val credentialProofKeyAcceptance: CredentialProofKeyAcceptance? = null,
    /** Commits proof-key side effects only after the credential was constructed successfully. */
    private val credentialProofKeyCommitment: CredentialProofKeyCommitment? = null,
    private val credentialProofVerifier: CredentialProofVerifier = DefaultCredentialProofVerifier(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    suspend fun processPushedAuthorizationRequest(
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>> = emptyMap(),
        requestId: String,
    ): PushedAuthorizationResponseHttp {
        val parRequest = when (
            val requestResult = try {
                oauth2Provider.createPushedAuthorizationRequest(parameters, headers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "PAR request validation failed (requestId=$requestId)" }
                val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "PAR processing failed")
                val response = oauth2Provider.writePushedAuthorizationError(error)
                notificationService.notify(
                    requestId = requestId,
                    session = null,
                    event = IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED,
                    error = error.error,
                    errorDescription = error.description,
                )
                return response
            }
        ) {
            is AuthorizationRequestResult.Success -> requestResult.request
            is AuthorizationRequestResult.Failure -> {
                val response = oauth2Provider.writePushedAuthorizationError(requestResult.error)
                notificationService.notify(
                    requestId = requestId,
                    session = null,
                    event = IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED,
                    error = requestResult.error.error,
                    errorDescription = requestResult.error.description,
                )
                return response
            }
        }

        val resolvedSession = try {
            parRequest.issuerState?.let { sessionService.getSessionOrNull(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "PAR session resolution failed (requestId=$requestId)" }
            val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "PAR processing failed")
            val response = oauth2Provider.writePushedAuthorizationError(error)
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED,
                error = error.error,
                errorDescription = error.description,
            )
            return response
        }

        if (parRequest.issuerState != null && resolvedSession?.isActiveAuthorizationCodeSession() != true) {
            val error = OAuthError(OAuthErrorCodes.INVALID_REQUEST, "issuer_state is invalid")
            val response = oauth2Provider.writePushedAuthorizationError(parRequest, error)
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED,
                error = error.error,
                errorDescription = error.description,
            )
            return response
        }

        val (response, error) = try {
            when (val responseResult = oauth2Provider.createPushedAuthorizationResponse(parRequest)) {
                is PushedAuthorizationResponseResult.Success ->
                    oauth2Provider.writePushedAuthorizationResponse(
                        responseResult.request,
                        responseResult.response,
                    ) to null

                is PushedAuthorizationResponseResult.Failure ->
                    oauth2Provider.writePushedAuthorizationError(parRequest, responseResult.error) to responseResult.error
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) {
                "PAR response creation failed (requestId=$requestId, sessionId=${resolvedSession?.sessionId})"
            }
            val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "PAR processing failed")
            oauth2Provider.writePushedAuthorizationError(error) to error
        }

        notificationService.notify(
            requestId = requestId,
            session = resolvedSession,
            event = if (error == null) {
                IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_SUCCEEDED
            } else {
                IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED
            },
            error = error?.error,
            errorDescription = error?.description,
        )
        return response
    }

    suspend fun processAuthorizeRequest(
        parameters: Map<String, List<String>>,
        requestId: String,
    ): AuthorizationResponseHttp {
        val authorizationRequest = try {
            when (val result = oauth2Provider.createAuthorizationRequest(parameters)) {
                is AuthorizationRequestResult.Success -> result.request.withIssuer(metadataService.issuerBaseUrl())
                is AuthorizationRequestResult.Failure -> {
                    notificationService.notify(
                        requestId = requestId,
                        session = null,
                        event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                        error = result.error.error,
                        errorDescription = result.error.description,
                    )
                    return oauth2Provider.writeAuthorizationError(result.error)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationError)
        }
        val resolvedParameters = authorizationRequest.requestForm

        val issuanceSession = try {
            resolveAuthorizationSession(authorizationRequest, resolvedParameters)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationRequest, authorizationError)
        }
        if (!issuanceSession.isActiveAuthorizationCodeSession()) {
            val authorizationError = OAuthError(
                OAuthErrorCodes.INVALID_REQUEST,
                "issuer_state is invalid",
            )
            notificationService.notify(
                requestId = requestId,
                session = issuanceSession,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationRequest, authorizationError)
        }
        val internalAuthorizationRequest =
            resolvedParameters.withInternalAuthorizationSession(issuanceSession.sessionId)
        val authorizationRequestEnvelope = try {
            internalAuthorizationRequest.encodeExternalLoginAuthorizationParameters()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = issuanceSession,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationRequest, authorizationError)
        }

        val redirectUri =
            "${metadataService.issuerBaseUrl()}/external_login/$authorizationRequestEnvelope"
        return AuthorizationResponseHttp(
            status = 302,
            redirectUri = redirectUri,
            headers = mapOf("Location" to redirectUri),
        )
    }

    suspend fun processExternalLoginInterception(
        externalAuthorizationRequest: String?,
        authorizationRequestEnvelope: String?,
        requestId: String,
    ) {
        val (authorizationRequestParameters, session) = try {
            val decodedAuthorizationRequestParameters = authorizationRequestEnvelope
                ?.takeIf { it.isNotBlank() }
                ?.decodeExternalLoginAuthorizationParameters()
                ?: throw IllegalArgumentException("Missing authorization request envelope")
            val authorizationRequestParameters =
                when (val result = oauth2Provider.createAuthorizationRequest(decodedAuthorizationRequestParameters)) {
                    is AuthorizationRequestResult.Success -> result.request.requestForm
                    is AuthorizationRequestResult.Failure -> throw IllegalArgumentException(
                        result.error.description ?: result.error.error
                    )
                }

            val sessionId = authorizationRequestParameters[INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER]?.singleOrNull()
                ?: authorizationRequestParameters["issuer_state"]?.singleOrNull()
                ?: throw IllegalArgumentException("Missing issuance session id in internal authorization request")

            authorizationRequestParameters to sessionService.getSession(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            throw e
        }

        try {
            require(session.isActiveAuthorizationCodeSession()) { "issuer_state is invalid" }

            val externalState = externalAuthorizationRequest
                ?.substringAfter("?", missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() }
                ?.let { parseQueryParameters(it)["state"]?.singleOrNull() }
                ?: throw IllegalArgumentException("Missing state in external authorization request")

            sessionService.saveSession(
                session.copy(
                    authorizationRequest = authorizationRequestParameters.withoutInternalAuthorizationSession(),
                    externalAuthorizationState = externalState,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = session,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            throw e
        }
    }

    suspend fun processExternalAuthorizationCallback(
        authServerState: String?,
        idToken: String?,
        requestId: String,
    ): AuthorizationResponseHttp {
        val resolvedAuthServerState = authServerState?.takeIf { it.isNotBlank() } ?: run {
            val authorizationError = OAuthError(
                OAuthErrorCodes.INVALID_REQUEST,
                "state parameter is missing in the callback request",
            )
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationError)
        }

        val session = try {
            sessionService.findByExternalAuthorizationState(resolvedAuthServerState)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationError)
        } ?: run {
            val authorizationError = OAuthError(
                OAuthErrorCodes.INVALID_REQUEST,
                "No issuance session found for external OAuth state",
            )
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationError)
        }

        val authorizationRequestParameters = session.authorizationRequest ?: run {
            val authorizationError = OAuthError(
                OAuthErrorCodes.INVALID_REQUEST,
                "Session has no stored authorization request",
            )
            notificationService.notify(
                requestId = requestId,
                session = session,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationError)
        }

        val authorizationRequest = try {
            when (val result = oauth2Provider.createAuthorizationRequest(authorizationRequestParameters)) {
                is AuthorizationRequestResult.Success -> result.request.withIssuer(metadataService.issuerBaseUrl())
                is AuthorizationRequestResult.Failure -> {
                    notificationService.notify(
                        requestId = requestId,
                        session = session,
                        event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                        error = result.error.error,
                        errorDescription = result.error.description,
                    )
                    return oauth2Provider.writeAuthorizationError(result.error)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Could not restore authorization request for issuance session ${session.sessionId}" }
            val authorizationError = OAuthError(
                OAuthErrorCodes.SERVER_ERROR,
                "Could not restore the authorization request",
            )
            notificationService.notify(
                requestId = requestId,
                session = session,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationError)
        }

        if (!session.isActiveAuthorizationCodeSession()) {
            val authorizationError = OAuthError(OAuthErrorCodes.INVALID_REQUEST, "issuer_state is invalid")
            notificationService.notify(
                requestId = requestId,
                session = session,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationRequest, authorizationError)
        }

        val resolvedIdToken = idToken?.takeIf { it.isNotBlank() } ?: run {
            val authorizationError = OAuthError(
                OAuthErrorCodes.INVALID_REQUEST,
                "id_token is missing in the callback request",
            )
            notificationService.notify(
                requestId = requestId,
                session = session,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationRequest, authorizationError)
        }

        val idTokenClaims = try {
            resolvedIdToken.decodeJws().payload
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Could not decode external id_token for issuance session ${session.sessionId}" }
            val authorizationError = OAuthError(
                OAuthErrorCodes.SERVER_ERROR,
                "Could not process the external identity token",
            )
            notificationService.notify(
                requestId = requestId,
                session = session,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationRequest, authorizationError)
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
            logger.warn(e) { "Could not map id_token claims for issuance session ${session.sessionId}" }
            val authorizationError = OAuthError(
                OAuthErrorCodes.SERVER_ERROR,
                "Could not map external identity claims to credential data",
            )
            notificationService.notify(
                requestId = requestId,
                session = session,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(authorizationRequest, authorizationError)
        }

        val updatedSession = session.copy(
            credentialData = credentialData,
            authorizationClaims = idTokenClaims,
            externalAuthorizationState = null,
        )

        return createAuthorizationResponse(
            issuanceSession = updatedSession,
            authorizationRequest = authorizationRequest,
            parameters = authorizationRequestParameters,
            claims = idTokenClaims,
            requestId = requestId,
        )
    }

    suspend fun processTokenRequest(
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>> = emptyMap(),
        requestId: String,
    ): AccessTokenResponseHttp {
        val requestedGrantTypes = parameters["grant_type"]?.singleOrNull()?.let(::setOf).orEmpty()
        val accessTokenRequest = when (
            val result = try {
                oauth2Provider.createAccessTokenRequest(
                    parameters = parameters,
                    headers = headers,
                    tokenEndpointUri = endpointUri(TOKEN_ENDPOINT_PATH),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Token request validation failed (requestId=$requestId)" }
                val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "Token request processing failed")
                val response = oauth2Provider.writeAccessTokenError(error)
                notificationService.notify(
                    requestId = requestId,
                    session = null,
                    event = tokenRequestFailureEvent(requestedGrantTypes),
                    error = error.error,
                    errorDescription = error.description,
                )
                return response
            }
        ) {
            is AccessTokenRequestResult.Success -> result.request.withIssuer(metadataService.issuerBaseUrl())
            is AccessTokenRequestResult.Failure -> {
                val response = oauth2Provider.writeAccessTokenError(result.error)
                notificationService.notify(
                    requestId = requestId,
                    session = null,
                    event = tokenRequestFailureEvent(requestedGrantTypes),
                    error = result.error.error,
                    errorDescription = result.error.description,
                )
                return response
            }
        }

        val result = try {
            oauth2Provider.createAccessTokenResponse(accessTokenRequest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Token response creation failed (requestId=$requestId)" }
            val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "Token request processing failed")
            val response = oauth2Provider.writeAccessTokenError(accessTokenRequest, error)
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = tokenRequestFailureEvent(accessTokenRequest.grantTypes),
                error = error.error,
                errorDescription = error.description,
            )
            return response
        }

        return when (result) {
            is AccessTokenResponseResult.Failure -> {
                val correlatedSession = result.request.session?.subject
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sessionId ->
                        try {
                            sessionService.getSessionOrNull(sessionId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.warn(e) { "Could not load issuance session $sessionId for token failure" }
                            null
                        }
                    }
                val response = oauth2Provider.writeAccessTokenError(result.request, result.error)
                notificationService.notify(
                    requestId = requestId,
                    session = correlatedSession,
                    event = tokenRequestFailureEvent(result.request.grantTypes),
                    error = result.error.error,
                    errorDescription = result.error.description,
                )
                response
            }

            is AccessTokenResponseResult.Success -> {
                val failureEvent = tokenRequestFailureEvent(result.request.grantTypes)
                val sessionId = result.request.session?.subject?.takeIf { it.isNotBlank() }
                if (sessionId == null) {
                    val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "Token request has no issuance session")
                    val response = oauth2Provider.writeAccessTokenError(result.request, error)
                    notificationService.notify(
                        requestId = requestId,
                        session = null,
                        event = failureEvent,
                        error = error.error,
                        errorDescription = error.description,
                    )
                    return response
                }

                val session = try {
                    sessionService.getSessionOrNull(sessionId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Could not load issuance session $sessionId for token success" }
                    null
                }
                if (session == null) {
                    val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "Issuance session is unavailable")
                    val response = oauth2Provider.writeAccessTokenError(result.request, error)
                    notificationService.notify(
                        requestId = requestId,
                        session = null,
                        event = failureEvent,
                        error = error.error,
                        errorDescription = error.description,
                    )
                    return response
                }

                val event = tokenRequestEvent(result.request.grantTypes, succeeded = true)
                if (event == null) {
                    val error = OAuthError(
                        OAuthErrorCodes.SERVER_ERROR,
                        "Token response has no supported grant type",
                    )
                    val response = oauth2Provider.writeAccessTokenError(result.request, error)
                    notificationService.notify(
                        requestId = requestId,
                        session = session,
                        event = failureEvent,
                        error = error.error,
                        errorDescription = error.description,
                    )
                    return response
                }

                val response = oauth2Provider.writeAccessTokenResponse(result.request, result.response)
                notificationService.notify(
                    requestId = requestId,
                    session = session,
                    event = event,
                )
                response
            }
        }
    }

    suspend fun processCredentialRequest(
        authorizationHeaders: List<String>,
        dpopProofHeaderValues: List<String>,
        parameters: JsonObject,
        requestId: String,
    ): CredentialResponseHttp {
        val authorization = parseCredentialAuthorization(authorizationHeaders)
            ?: return invalidCredentialAuthorization(requestId)
        val parameterMap = parameters.toParametersMap()
        return processCredentialRequest(authorization.token, requestId) {
            oauth2Provider.createCredentialRequest(
                parameters = parameterMap,
                accessTokenContext = CredentialAccessTokenContext(
                    authorization = authorization,
                    expectedIssuer = metadataService.issuerBaseUrl(),
                    dpopProofHeaderValues = dpopProofHeaderValues,
                    credentialEndpointUri = endpointUri(CREDENTIAL_ENDPOINT_PATH),
                ),
            )
        }
    }

    suspend fun processCredentialRequest(
        authorizationHeaders: List<String>,
        dpopProofHeaderValues: List<String>,
        encryptedCredentialRequest: String,
        requestId: String,
    ): CredentialResponseHttp {
        val authorization = parseCredentialAuthorization(authorizationHeaders)
            ?: return invalidCredentialAuthorization(requestId)
        return processCredentialRequest(authorization.token, requestId) {
            oauth2Provider.createCredentialRequest(
                encryptedCredentialRequest = encryptedCredentialRequest,
                accessTokenContext = CredentialAccessTokenContext(
                    authorization = authorization,
                    expectedIssuer = metadataService.issuerBaseUrl(),
                    dpopProofHeaderValues = dpopProofHeaderValues,
                    credentialEndpointUri = endpointUri(CREDENTIAL_ENDPOINT_PATH),
                ),
            )
        }
    }

    private fun parseCredentialAuthorization(authorizationHeaders: List<String>) =
        runCatching { parseAccessTokenAuthorization(authorizationHeaders) }.getOrNull()

    private suspend fun invalidCredentialAuthorization(requestId: String): CredentialResponseHttp {
        val error = OAuthError(
            OAuthErrorCodes.INVALID_TOKEN,
            "Credential request has invalid authorization credentials",
        )
        val response = oauth2Provider.writeCredentialError(error)
        notificationService.notify(
            requestId = requestId,
            session = null,
            event = IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
            error = error.error,
            errorDescription = error.description,
        )
        return response
    }

    private suspend fun processCredentialRequest(
        accessToken: String,
        requestId: String,
        createCredentialRequest: suspend () -> CredentialRequestResult,
    ): CredentialResponseHttp {
        val credentialRequest = when (
            val result = try {
                createCredentialRequest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notificationService.notify(
                    requestId = requestId,
                    session = null,
                    event = IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
                    error = OAuthErrorCodes.SERVER_ERROR,
                    errorDescription = "Credential request processing failed",
                )
                throw e
            }
        ) {
            is CredentialRequestResult.Success -> result.request
            is CredentialRequestResult.Failure -> {
                val response = oauth2Provider.writeCredentialError(result.error)
                notificationService.notify(
                    requestId = requestId,
                    session = null,
                    event = IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
                    error = result.error.error,
                    errorDescription = result.error.description,
                )
                return response
            }
            // Parsing, access-token, and DPoP failures have no trusted issuance-session
            // correlation. Do not decode an unverified token merely to publish an event.
            is CredentialRequestResult.OAuthFailure -> {
                val response = oauth2Provider.writeCredentialError(result.error)
                notificationService.notify(
                    requestId = requestId,
                    session = null,
                    event = IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
                    error = result.error.error,
                    errorDescription = result.error.description,
                )
                return response
            }
        }
        val tokenClaims = try {
            accessToken.decodeJws().payload
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = OAuthError(OAuthErrorCodes.INVALID_TOKEN, e.message)
            val response = oauth2Provider.writeCredentialError(credentialRequest, error)
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
                error = error.error,
                errorDescription = error.description,
            )
            return response
        }

        val sessionId = tokenClaims.stringClaim("sub") ?: run {
            val error = OAuthError(OAuthErrorCodes.INVALID_TOKEN, "Access token has no session id")
            val response = oauth2Provider.writeCredentialError(credentialRequest, error)
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
                error = error.error,
                errorDescription = error.description,
            )
            return response
        }
        val observedSession = try {
            sessionService.getSession(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = OAuthError(OAuthErrorCodes.INVALID_TOKEN, e.message)
            val response = oauth2Provider.writeCredentialError(
                credentialRequest.withSession(DefaultSession(subject = sessionId)),
                error,
            )
            notificationService.notify(
                requestId = requestId,
                session = null,
                event = IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
                error = error.error,
                errorDescription = error.description,
            )
            return response
        }
        val issuerId = observedSession.issuerDid ?: metadataService.issuerBaseUrl()
        val requestWithSession = credentialRequest
            .withSession(DefaultSession(subject = sessionId))
            .withIssuer(issuerId)

        // This trusted configuration classifies the event; it does not short-circuit the protocol checks below.
        val sessionConfiguration = metadataService.getCredentialConfiguration(observedSession.credentialConfigurationId)

        if (observedSession.isClosed || observedSession.status != IssuanceSessionStatus.ACTIVE) {
            return rejectCredentialRequest(
                requestWithSession,
                observedSession,
                sessionConfiguration?.format,
                requestId,
                CredentialError(
                    CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                    "Issuance session is already closed",
                ),
            )
        }

        val credentialConfigurationId = when (
            val resolution = credentialRequest.resolveCredentialConfigurationId(
                credentialConfigurationExists = { metadataService.getCredentialConfiguration(it) != null },
                resolveCredentialIdentifier = { identifier ->
                    observedSession.credentialConfigurationId.takeIf { it == identifier }
                },
            )
        ) {
            is CredentialRequestTargetResolution.Success -> resolution.credentialConfigurationId
            is CredentialRequestTargetResolution.Failure -> {
                return failCredentialRequest(
                    requestWithSession,
                    observedSession,
                    sessionConfiguration?.format,
                    requestId,
                    resolution.error,
                )
            }
        }

        if (observedSession.credentialConfigurationId != credentialConfigurationId) {
            return failCredentialRequest(
                requestWithSession,
                observedSession,
                sessionConfiguration?.format,
                requestId,
                CredentialError(
                    CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                    "Credential request references $credentialConfigurationId, but session ${observedSession.sessionId} is for ${observedSession.credentialConfigurationId}",
                ),
            )
        }

        val configuration = sessionConfiguration ?: return failCredentialRequest(
            requestWithSession,
            observedSession,
            null,
            requestId,
            CredentialError(
                CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
                "Unsupported credential_configuration_id: $credentialConfigurationId",
            ),
        )

        val nonceBinding = credentialNonceBinding()

        // The provider validates proofs authoritatively while building the credential response. Sessions
        // pinned to an expected holder key, and deployments hooking into proof-key acceptance, need that
        // key before the session is claimed, so resolve it up front for those cases only. Nonces stay
        // valid until they expire, so validating them here as well never consumes anything.
        val proofPublicKeyJwk = if (requiresCredentialProofKey(observedSession)) {
            try {
                resolveCredentialProofPublicKeyJwk(requestWithSession, configuration, nonceBinding)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CredentialProofValidationException) {
                return rejectCredentialRequest(
                    requestWithSession,
                    observedSession,
                    configuration.format,
                    requestId,
                    CredentialError(e.errorCode, e.message),
                )
            } catch (e: Exception) {
                return rejectCredentialRequest(
                    requestWithSession,
                    observedSession,
                    configuration.format,
                    requestId,
                    CredentialError(
                        CredentialErrorCodes.INVALID_PROOF,
                        e.message ?: "Credential proof is invalid",
                    ),
                )
            }
        } else {
            null
        }

        validateExpectedCredentialProofKey(proofPublicKeyJwk, observedSession)?.let { error ->
            return rejectCredentialRequest(
                requestWithSession,
                observedSession,
                configuration.format,
                requestId,
                error,
            )
        }

        // Claiming removes the session, so every later exit has to either close or restore it.
        val session = sessionService.claimSession(sessionId)
            ?: return rejectCredentialRequest(
                requestWithSession,
                observedSession,
                configuration.format,
                requestId,
                CredentialError(
                    CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                    "Issuance session is already closed or being processed",
                ),
            )

        var claimFinalized = false
        var committedProofKeyJwk: JsonObject? = null
        try {
            val issuerKey = KeyManager.resolveSerializedKey(session.issuerKey)
            val crypto2IssuerKey = restoreSessionIssuerCrypto2Key(session, crypto2Runtime)
            val x5Chain = session.x5Chain?.map { X509CertificateUtil.parseCertificatePem(it) }

            credentialProofKeyAcceptance?.let { acceptance ->
                val accepted = try {
                    acceptance.accept(session, requireNotNull(proofPublicKeyJwk))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CredentialProofKeyAcceptanceException) {
                    val response = if (e.retryable) {
                        retryCredentialRequest(
                            requestWithSession,
                            session,
                            configuration.format,
                            requestId,
                            e.toCredentialError(),
                        )
                    } else {
                        failClaimedCredentialRequest(
                            requestWithSession,
                            session,
                            configuration.format,
                            requestId,
                            e.toCredentialError(),
                        )
                    }
                    claimFinalized = true
                    return response
                } catch (e: Exception) {
                    val response = retryCredentialRequest(
                        requestWithSession,
                        session,
                        configuration.format,
                        requestId,
                        e.toCredentialProofError(),
                    )
                    claimFinalized = true
                    return response
                }
                if (!accepted) {
                    val response = retryCredentialRequest(
                        requestWithSession,
                        session,
                        configuration.format,
                        requestId,
                        CredentialError(
                            CredentialErrorCodes.INVALID_PROOF,
                            "Credential proof key was not accepted",
                        ),
                    )
                    claimFinalized = true
                    return response
                }
            }

            // Prepare credential data with status injection for W3C/IETF formats
            val credentialDataWithStatus = session.credentialStatus?.let { status ->
                when (configuration.format) {
                    CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC, CredentialFormat.JWT_VC_JSON_LD -> {
                        // Inject credentialStatus into credential data for W3C VCs
                        JsonObject(session.credentialData.toMutableMap().apply {
                            put("credentialStatus", status)
                        })
                    }

                    CredentialFormat.SD_JWT_VC -> {
                        // For SD-JWT VC, inject status at root level (as "status" claim)
                        JsonObject(session.credentialData.toMutableMap().apply {
                            put("status", status)
                        })
                    }

                    else -> session.credentialData
                }
            } ?: session.credentialData

            // Convert credentialStatus to Status object for mDoc
            val mDocStatus = session.credentialStatus?.let { status ->
                when (configuration.format) {
                    CredentialFormat.MSO_MDOC -> parseStatusFromJsonElement(status)
                    else -> null
                }
            }

            val proofValidationContext = CredentialProofValidationContext(
                credentialIssuer = nonceBinding.credentialIssuer,
                clientId = requestWithSession.accessTokenClientId,
                anonymousPreAuthorizedAccess = requestWithSession.anonymousPreAuthorizedAccess,
                nonceValidation = CredentialNonceValidationContext(
                    service = credentialNonceService,
                    binding = nonceBinding,
                ),
            )
            val credentialResponseResult = if (crypto2IssuerKey != null) {
                oauth2Provider.createCredentialResponse(
                    request = requestWithSession,
                    configuration = configuration,
                    issuerKey = Crypto2CredentialSigningKey.select(crypto2IssuerKey, configuration),
                    issuerId = issuerId,
                    credentialData = credentialDataWithStatus,
                    dataMapping = session.mapping,
                    selectiveDisclosure = session.selectiveDisclosure,
                    x5Chain = x5Chain,
                    mDocNameSpacesDataMappingConfig = session.mDocNameSpacesDataMappingConfig,
                    authorizedTransactionDataTypes = session.authorizedTransactionDataTypes,
                    credentialStatus = mDocStatus,
                    proofValidationContext = proofValidationContext,
                )
            } else {
                oauth2Provider.createCredentialResponse(
                    request = requestWithSession,
                    configuration = configuration,
                    issuerKey = issuerKey,
                    issuerId = issuerId,
                    credentialData = credentialDataWithStatus,
                    dataMapping = session.mapping,
                    selectiveDisclosure = session.selectiveDisclosure,
                    x5Chain = x5Chain,
                    mDocNameSpacesDataMappingConfig = session.mDocNameSpacesDataMappingConfig,
                    authorizedTransactionDataTypes = session.authorizedTransactionDataTypes,
                    credentialStatus = mDocStatus,
                    proofValidationContext = proofValidationContext,
                )
            }
            val credentialResponse = when (val result = credentialResponseResult) {
                is CredentialResponseResult.Success -> result.response
                is CredentialResponseResult.Failure -> {
                    val response = if (result.error.isRetryableProofFailure()) {
                        retryCredentialRequest(
                            requestWithSession,
                            session,
                            configuration.format,
                            requestId,
                            result.error,
                        )
                    } else {
                        failClaimedCredentialRequest(
                            requestWithSession,
                            session,
                            configuration.format,
                            requestId,
                            result.error,
                        )
                    }
                    claimFinalized = true
                    return response
                }
            }

            credentialProofKeyCommitment?.let { commitment ->
                val committed = try {
                    commitment.commit(session, requireNotNull(proofPublicKeyJwk))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CredentialProofKeyAcceptanceException) {
                    val response = if (e.retryable) {
                        retryCredentialRequest(
                            requestWithSession,
                            session,
                            configuration.format,
                            requestId,
                            e.toCredentialError(),
                        )
                    } else {
                        failClaimedCredentialRequest(
                            requestWithSession,
                            session,
                            configuration.format,
                            requestId,
                            e.toCredentialError(),
                        )
                    }
                    claimFinalized = true
                    return response
                } catch (e: Exception) {
                    val response = retryCredentialRequest(
                        requestWithSession,
                        session,
                        configuration.format,
                        requestId,
                        e.toCredentialProofError(),
                    )
                    claimFinalized = true
                    return response
                }
                if (!committed) {
                    claimFinalized = true
                    return failClaimedCredentialRequest(
                        requestWithSession,
                        session,
                        configuration.format,
                        requestId,
                        CredentialError(
                            CredentialErrorCodes.INVALID_PROOF,
                            "Credential proof key could not be committed",
                        ),
                    )
                }
                committedProofKeyJwk = proofPublicKeyJwk
            }

            val response = oauth2Provider.writeCredentialResponse(requestWithSession, credentialResponse)
            val updatedSession = try {
                withContext(NonCancellable) {
                    sessionService.saveSession(
                        session.copy(
                            status = IssuanceSessionStatus.SUCCESSFUL,
                            statusReason = "Credential issued successfully",
                            issuedCredentialFormat = configuration.format.value,
                            isClosed = true,
                        )
                    )
                }
            } catch (e: Exception) {
                val retrySession = session.copy(
                    expectedCredentialProofKeyJwk = session.expectedCredentialProofKeyJwk ?: proofPublicKeyJwk,
                )
                try {
                    restoreClaimedSession(retrySession)
                } catch (restoreException: Exception) {
                    e.addSuppressed(restoreException)
                    throw e
                }
                claimFinalized = true
                val error = OAuthError(
                    OAuthErrorCodes.SERVER_ERROR,
                    "Credential finalization failed; retry the request",
                )
                val errorResponse = oauth2Provider.writeCredentialError(
                    requestWithSession,
                    error,
                )
                notificationService.notify(
                    requestId = requestId,
                    session = retrySession,
                    event = credentialRequestEvent(configuration.format, succeeded = false),
                    error = error.error,
                    errorDescription = error.description,
                )
                return errorResponse
            }
            claimFinalized = true

            notificationService.notify(
                requestId = requestId,
                session = updatedSession,
                event = credentialRequestEvent(configuration.format, succeeded = true),
            )
            notificationService.emitIssuanceStatus(requestId, updatedSession)

            return response
        } catch (e: CancellationException) {
            if (!claimFinalized) {
                restoreClaimedSession(
                    session.copy(
                        expectedCredentialProofKeyJwk = session.expectedCredentialProofKeyJwk ?: committedProofKeyJwk,
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            if (claimFinalized) {
                throw e
            }
            claimFinalized = true
            return failClaimedCredentialRequest(
                requestWithSession,
                session,
                configuration.format,
                requestId,
                e.toCredentialServerError(),
            )
        }
    }

    suspend fun processNonceRequest(
        requestId: String,
    ): NonceResponseHttp {
        val (response, error) = try {
            val issuedNonce = credentialNonceService.issue(credentialNonceBinding())
            NonceResponseHttp(
                status = 200,
                payload = mapOf("c_nonce" to JsonPrimitive(issuedNonce.nonce)),
                headers = mapOf("Cache-Control" to "no-store"),
            ) to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Nonce request processing failed (requestId=$requestId)" }
            val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "Nonce request processing failed")
            NonceResponseHttp(
                status = 500,
                payload = buildMap {
                    put("error", JsonPrimitive(error.error))
                    error.description?.let { put("error_description", JsonPrimitive(it)) }
                },
                headers = mapOf("Cache-Control" to "no-store"),
            ) to error
        }

        notificationService.notify(
            requestId = requestId,
            session = null,
            event = if (error == null) {
                IssuanceSessionEvent.NONCE_REQUEST_SUCCEEDED
            } else {
                IssuanceSessionEvent.NONCE_REQUEST_FAILED
            },
            error = error?.error,
            errorDescription = error?.description,
        )
        return response
    }

    private fun credentialNonceBinding(): CredentialNonceBinding {
        val metadata = metadataService.getCredentialIssuerMetadata()
        return CredentialNonceBinding(
            credentialIssuer = metadata.credentialIssuer,
            credentialEndpoint = metadata.credentialEndpoint,
            nonceEndpoint = requireNotNull(metadata.nonceEndpoint) {
                "Credential issuer metadata must expose a nonce endpoint"
            },
        )
    }

    private suspend fun createAuthorizationResponse(
        issuanceSession: IssuanceSession,
        authorizationRequest: AuthorizationRequest,
        parameters: Map<String, List<String>>,
        claims: JsonObject?,
        requestId: String,
    ): AuthorizationResponseHttp {
        val requestWithIssuer = authorizationRequest.withIssuer(metadataService.issuerBaseUrl())
        val oauthSession = DefaultSession(subject = issuanceSession.sessionId)

        val authorizationResponse = try {
            when (val result = oauth2Provider.createAuthorizationResponse(requestWithIssuer, oauthSession)) {
                is AuthorizationResponseResult.Success -> result.response
                is AuthorizationResponseResult.Failure -> {
                    notificationService.notify(
                        requestId = requestId,
                        session = issuanceSession,
                        event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                        error = result.error.error,
                        errorDescription = result.error.description,
                    )
                    return oauth2Provider.writeAuthorizationError(
                        requestWithIssuer,
                        result.error,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = issuanceSession,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(requestWithIssuer, authorizationError)
        }

        val updatedSession = issuanceSession.copy(
            authorizationRequest = parameters,
            authorizationClaims = claims ?: issuanceSession.authorizationClaims,
            externalAuthorizationState = null,
        )
        try {
            sessionService.saveSession(updatedSession)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = issuanceSession,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(requestWithIssuer, authorizationError)
        }

        val response = try {
            oauth2Provider.writeAuthorizationResponse(requestWithIssuer, authorizationResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val authorizationError = e.toAuthorizationError()
            notificationService.notify(
                requestId = requestId,
                session = updatedSession,
                event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED,
                error = authorizationError.error,
                errorDescription = authorizationError.description,
            )
            return oauth2Provider.writeAuthorizationError(requestWithIssuer, authorizationError)
        }
        notificationService.notify(
            requestId = requestId,
            session = updatedSession,
            event = IssuanceSessionEvent.AUTHORIZATION_REQUEST_SUCCEEDED,
        )

        return response
    }

    private suspend fun resolveAuthorizationSession(
        authorizationRequest: AuthorizationRequest,
        parameters: Map<String, List<String>>,
    ): IssuanceSession =
        authorizationRequest.issuerState
            ?.let { sessionId ->
                requireNotNull(sessionService.getSessionOrNull(sessionId)) { "issuer_state is invalid" }
            }
            ?: createAuthorizationCodeSessionFromProfile(authorizationRequest, parameters)

    private fun IssuanceSession.isActiveAuthorizationCodeSession(): Boolean =
        authenticationMethod == AuthenticationMethod.AUTHORIZED &&
            status == IssuanceSessionStatus.ACTIVE &&
            !isClosed &&
            expiresAt > Clock.System.now()

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
            authorizedTransactionDataTypes = authorizedTransactionDataTypes,
            x5Chain = x5Chain,
            issuerDid = issuerDid,
            authorizationRequest = authorizationRequest,
            expiresAt = Clock.System.now() + AUTHORIZATION_CODE_SESSION_LIFETIME,
            notifications = notifications,
            credentialStatus = credentialStatus,
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
            key to listOf(
                if (value is JsonPrimitive && value.isString) {
                    value.content
                } else {
                    value.toString()
                }
            )
        }

    private fun JsonObject.stringClaim(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun requiresCredentialProofKey(session: IssuanceSession): Boolean =
        credentialProofKeyAcceptance != null ||
                credentialProofKeyCommitment != null ||
                session.expectedCredentialProofKeyJwk != null

    /**
     * Resolves the holder key of the request's credential proof.
     *
     * The credential response creation validates proofs authoritatively as well. This only exposes the
     * holder key to session pinning and to the proof-key hooks before the session is claimed, so that
     * neither runs for a request that cannot be issued.
     */
    private suspend fun resolveCredentialProofPublicKeyJwk(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        nonceBinding: CredentialNonceBinding,
    ): JsonObject {
        val verifiedProof = credentialProofVerifier.verify(
            credentialRequest = request,
            credentialConfiguration = configuration,
            context = CredentialProofValidationContext(
                credentialIssuer = nonceBinding.credentialIssuer,
                clientId = request.accessTokenClientId,
                anonymousPreAuthorizedAccess = request.anonymousPreAuthorizedAccess,
                nonceValidation = CredentialNonceValidationContext(
                    service = credentialNonceService,
                    binding = nonceBinding,
                ),
            ),
        ).firstOrNull() ?: throw IllegalArgumentException("Credential request has no credential proof")
        // The verified holder key is a crypto2 key since the crypto updates, so the public JWK comes
        // from its public key exporter rather than the legacy getPublicKey().exportJWKObject().
        val holderKey = verifiedProof.holderKey
        val holderPublicJwk = requireNotNull(holderKey.capabilities.publicKeyExporter) {
            "Credential proof holder key does not export public material"
        }.exportPublicKey().toPublicJwk(holderKey.spec)
        return Json.parseToJsonElement(holderPublicJwk.data.toByteArray().decodeToString()).jsonObject
    }

    private suspend fun validateExpectedCredentialProofKey(
        proofPublicKeyJwk: JsonObject?,
        session: IssuanceSession,
    ): CredentialError? {
        val expectedJwk = session.expectedCredentialProofKeyJwk ?: return null
        return try {
            val expectedKey = JWKKey.importJWK(expectedJwk.toString()).getOrThrow()
            val presentedKey = JWKKey.importJWK(
                requireNotNull(proofPublicKeyJwk) { "Credential proof key is missing" }.toString()
            ).getOrThrow()
            require(presentedKey.getThumbprint() == expectedKey.getThumbprint()) {
                "Credential proof key does not match the expected key"
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CredentialError(
                CredentialErrorCodes.INVALID_PROOF,
                "Credential proof key does not match the issuance session",
            )
        }
    }
    /** Reports a retryable rejection without changing the issuance-session lifecycle. */
    private suspend fun rejectCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat?,
        requestId: String,
        error: CredentialError,
    ): CredentialResponseHttp {
        notificationService.notify(
            requestId = requestId,
            session = session,
            event = credentialRequestEvent(format, succeeded = false),
            error = error.error,
            errorDescription = error.description,
        )
        return oauth2Provider.writeCredentialError(request, error)
    }

    private suspend fun failCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat?,
        requestId: String,
        error: CredentialError,
    ): CredentialResponseHttp {
        closeFailedIssuance(session, format, requestId, error.error, error.description)
        return oauth2Provider.writeCredentialError(request, error)
    }

    // The request failure reports the endpoint outcome; issuance status reports the terminal transition.
    private suspend fun closeFailedIssuance(
        session: IssuanceSession,
        format: CredentialFormat?,
        requestId: String,
        errorCode: String,
        errorDescription: String?,
    ) {
        logger.warn { "Credential request failed for session ${session.sessionId}: $errorCode - $errorDescription" }
        val failure = IssuanceSessionFailure(errorCode, errorDescription ?: errorCode)
        val updatedSession = sessionService.updateStatus(
            session.sessionId,
            IssuanceSessionStatus.UNSUCCESSFUL,
            errorDescription ?: errorCode,
            close = true,
            failure = failure,
        )
        notificationService.notify(
            requestId = requestId,
            session = updatedSession,
            event = credentialRequestEvent(format, succeeded = false),
            error = errorCode,
            errorDescription = errorDescription,
        )
        notificationService.emitIssuanceStatus(requestId, updatedSession)
    }

    /** Closes a claimed session, which is no longer readable through the repository. */
    private suspend fun failClaimedCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat,
        requestId: String,
        error: CredentialError,
    ): CredentialResponseHttp {
        closeClaimedSession(
            session.withFailure(error.error, error.description),
            format,
            requestId,
            error.description ?: error.error,
        )
        return oauth2Provider.writeCredentialError(request, error)
    }

    /** Closes a claimed session, which is no longer readable through the repository. */
    private suspend fun failClaimedCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat,
        requestId: String,
        error: OAuthError,
    ): CredentialResponseHttp {
        closeClaimedSession(
            session.withFailure(error.error, error.description),
            format,
            requestId,
            error.description ?: error.error,
        )
        return oauth2Provider.writeCredentialError(request, error)
    }

    private suspend fun closeClaimedSession(
        session: IssuanceSession,
        format: CredentialFormat,
        requestId: String,
        reason: String,
    ) {
        val failure = session.failure ?: IssuanceSessionFailure(
            error = OAuthErrorCodes.SERVER_ERROR,
            errorDescription = reason,
        )
        val updatedSession = withContext(NonCancellable) {
            sessionService.saveSession(
                session.copy(
                    status = IssuanceSessionStatus.UNSUCCESSFUL,
                    statusReason = reason,
                    isClosed = true,
                    failure = failure,
                )
            )
        }
        notificationService.notify(
            requestId = requestId,
            session = updatedSession,
            event = credentialRequestEvent(format, succeeded = false),
            error = failure.error,
            errorDescription = failure.errorDescription,
        )
        notificationService.emitIssuanceStatus(requestId, updatedSession)
    }

    /** Returns the claimed session unchanged so the wallet can retry the credential request. */
    private suspend fun retryCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat,
        requestId: String,
        error: CredentialError,
    ): CredentialResponseHttp {
        restoreClaimedSession(session)
        notificationService.notify(
            requestId = requestId,
            session = session,
            event = credentialRequestEvent(format, succeeded = false),
            error = error.error,
            errorDescription = error.description,
        )
        return oauth2Provider.writeCredentialError(request, error)
    }

    private suspend fun restoreClaimedSession(session: IssuanceSession) {
        withContext(NonCancellable) {
            sessionService.saveSession(session)
        }
    }

    private fun CredentialError.isRetryableProofFailure(): Boolean =
        error == CredentialErrorCodes.INVALID_PROOF || error == CredentialErrorCodes.INVALID_NONCE

    private fun CredentialProofKeyAcceptanceException.toCredentialError(): CredentialError =
        CredentialError(CredentialErrorCodes.INVALID_PROOF, message)

    private fun Exception.toCredentialProofError(): CredentialError =
        CredentialError(CredentialErrorCodes.INVALID_PROOF, message ?: "Credential proof key check failed")

    private fun credentialRequestEvent(
        format: CredentialFormat?,
        succeeded: Boolean,
    ): IssuanceSessionEvent = when (format) {
        null -> {
            require(!succeeded) { "A successful credential request must have a resolved format" }
            IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED
        }

        CredentialFormat.SD_JWT_VC -> if (succeeded) {
            IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_SUCCEEDED
        } else {
            IssuanceSessionEvent.CREDENTIAL_REQUEST_SD_JWT_VC_FAILED
        }

        CredentialFormat.JWT_VC_JSON,
        CredentialFormat.JWT_VC,
        CredentialFormat.JWT_VC_JSON_LD,
        CredentialFormat.LDP_VC -> if (succeeded) {
            IssuanceSessionEvent.CREDENTIAL_REQUEST_W3C_VC_SUCCEEDED
        } else {
            IssuanceSessionEvent.CREDENTIAL_REQUEST_W3C_VC_FAILED
        }

        CredentialFormat.MSO_MDOC -> if (succeeded) {
            IssuanceSessionEvent.CREDENTIAL_REQUEST_MSO_MDOC_SUCCEEDED
        } else {
            IssuanceSessionEvent.CREDENTIAL_REQUEST_MSO_MDOC_FAILED
        }
    }

    private fun tokenRequestEvent(
        grantTypes: Set<String>,
        succeeded: Boolean,
    ): IssuanceSessionEvent? = when {
        GrantType.AuthorizationCode.value in grantTypes -> if (succeeded) {
            IssuanceSessionEvent.TOKEN_REQUEST_AUTHORIZATION_CODE_SUCCEEDED
        } else {
            IssuanceSessionEvent.TOKEN_REQUEST_AUTHORIZATION_CODE_FAILED
        }

        GrantType.PreAuthorizedCode.value in grantTypes -> if (succeeded) {
            IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED
        } else {
            IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_FAILED
        }

        GrantType.RefreshToken.value in grantTypes -> if (succeeded) {
            IssuanceSessionEvent.TOKEN_REQUEST_REFRESH_TOKEN_SUCCEEDED
        } else {
            IssuanceSessionEvent.TOKEN_REQUEST_REFRESH_TOKEN_FAILED
        }

        !succeeded -> IssuanceSessionEvent.TOKEN_REQUEST_FAILED
        else -> null
    }

    private fun tokenRequestFailureEvent(grantTypes: Set<String>): IssuanceSessionEvent =
        tokenRequestEvent(grantTypes, succeeded = false) ?: IssuanceSessionEvent.TOKEN_REQUEST_FAILED

    private fun IssuanceSession.withFailure(error: String, errorDescription: String?): IssuanceSession =
        copy(failure = IssuanceSessionFailure(error = error, errorDescription = errorDescription))

    private suspend fun notifySessionEvent(
        sessionId: String?,
        event: IssuanceSessionEvent,
        authenticationMethod: AuthenticationMethod? = null,
        failure: IssuanceSessionFailure? = null,
    ) {
        if (sessionId.isNullOrBlank()) return
        val session = try {
            sessionService.getSessionOrNull(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Could not load issuance session $sessionId to emit event $event" }
            null
        }
        session
            ?.takeIf { authenticationMethod == null || it.authenticationMethod == authenticationMethod }
            ?.let { notificationService.notify(failure?.let { f -> it.copy(failure = f) } ?: it, event) }
    }

    private fun Map<String, List<String>>.withInternalAuthorizationSession(sessionId: String): Map<String, List<String>> =
        filterKeys { it != INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER } +
                (INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER to listOf(sessionId))

    private fun Map<String, List<String>>.withoutInternalAuthorizationSession(): Map<String, List<String>> =
        filterKeys { it != INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER }

    private fun parseQueryParameters(query: String): Map<String, List<String>> =
        parseQueryString(query).entries().associate { it.key to it.value }

    private fun endpointUri(path: String): String =
        "${metadataService.issuerBaseUrl().trimEnd('/')}/$path"

    private fun Exception.toAuthorizationError(): OAuthError =
        when (this) {
            is IllegalArgumentException,
            is NotFoundException -> OAuthError(OAuthErrorCodes.INVALID_REQUEST, message)

            else -> OAuthError(
                OAuthErrorCodes.SERVER_ERROR,
                message ?: "Authorization request processing failed",
            )
        }

    private fun Exception.toCredentialServerError(): OAuthError =
        OAuthError(
            OAuthErrorCodes.SERVER_ERROR,
            message ?: "Credential request processing failed",
        )

    /**
     * Parse JsonElement to Status object for mDoc credentials.
     * Supports status_list format with idx and uri fields.
     */
    private fun parseStatusFromJsonElement(status: JsonElement): MdocStatus? {
        return try {
            val statusObj = status.jsonObject
            val statusList = statusObj["status_list"]?.jsonObject
                ?: statusObj["statusList"]?.jsonObject
                ?: return null

            val idx = statusList["idx"]?.jsonPrimitive?.longOrNull?.toULong()
                ?: statusList["index"]?.jsonPrimitive?.longOrNull?.toULong()
                ?: return null
            val uri = statusList["uri"]?.jsonPrimitive?.content
                ?: return null

            MdocStatus(
                statusList = MdocStatusListInfo(
                    index = idx,
                    uri = id.walt.mdoc.objects.mso.UniformResourceIdentifier(uri)
                )
            )
        } catch (e: Exception) {
            null
        }
    }
}

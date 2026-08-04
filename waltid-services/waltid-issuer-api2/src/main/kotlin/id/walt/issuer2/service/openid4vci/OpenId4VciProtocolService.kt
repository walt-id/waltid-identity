package id.walt.issuer2.service.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.utils.JsonObjectPathMapper
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestTargetResolution
import id.walt.openid4vci.requests.credential.resolveCredentialConfigurationId
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.proofs.CredentialNonceBinding
import id.walt.openid4vci.proofs.CredentialNonceService
import id.walt.openid4vci.proofs.CredentialNonceValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.IssuedCredentialNonce
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.access.CredentialAccessTokenContext
import id.walt.openid4vci.tokens.access.parseAccessTokenAuthorization
import id.walt.mdoc.objects.mso.Status as MdocStatus
import id.walt.mdoc.objects.mso.Status.StatusListInfo as MdocStatusListInfo
import io.ktor.http.parseQueryString
import io.ktor.server.plugins.NotFoundException
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

private const val INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER = "_issuer2_session_id"
private const val TOKEN_ENDPOINT_PATH = "token"
private const val CREDENTIAL_ENDPOINT_PATH = "credential"
private val AUTHORIZATION_CODE_SESSION_LIFETIME = 5.minutes

class OpenId4VciProtocolService(
    private val oauth2Provider: OAuth2Provider,
    private val sessionService: IssuanceSessionService,
    private val profileService: CredentialProfileService,
    private val metadataService: MetadataService,
    private val notificationService: IssuanceNotificationService,
    private val credentialNonceService: CredentialNonceService,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun processPushedAuthorizationRequest(
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>> = emptyMap(),
    ): PushedAuthorizationResponseHttp {
        return try {
            val parRequest = when (val result = oauth2Provider.createPushedAuthorizationRequest(parameters, headers)) {
                is AuthorizationRequestResult.Success -> result.request
                is AuthorizationRequestResult.Failure -> return oauth2Provider.writePushedAuthorizationError(result.error)
            }

            when (val result = oauth2Provider.createPushedAuthorizationResponse(parRequest)) {
                is PushedAuthorizationResponseResult.Success ->
                    oauth2Provider.writePushedAuthorizationResponse(result.request, result.response)

                is PushedAuthorizationResponseResult.Failure ->
                    oauth2Provider.writePushedAuthorizationError(parRequest, result.error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            oauth2Provider.writePushedAuthorizationError(
                OAuthError(
                    error = OAuthErrorCodes.SERVER_ERROR,
                    description = "PAR processing failed: ${e.message}",
                )
            )
        }
    }

    suspend fun processAuthorizeRequest(parameters: Map<String, List<String>>): AuthorizationResponseHttp {
        val authorizationRequest = when (val result = oauth2Provider.createAuthorizationRequest(parameters)) {
            is AuthorizationRequestResult.Success -> result.request.withIssuer(metadataService.issuerBaseUrl())
            is AuthorizationRequestResult.Failure -> return oauth2Provider.writeAuthorizationError(result.error)
        }
        val resolvedParameters = authorizationRequest.requestForm

        val issuanceSession = try {
            resolveAuthorizationSession(authorizationRequest, resolvedParameters)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return oauth2Provider.writeAuthorizationError(authorizationRequest, e.toAuthorizationError())
        }
        val internalAuthorizationRequest =
            resolvedParameters.withInternalAuthorizationSession(issuanceSession.sessionId)
        val authorizationRequestEnvelope = try {
            internalAuthorizationRequest.encodeExternalLoginAuthorizationParameters()
        } catch (e: IllegalArgumentException) {
            return oauth2Provider.writeAuthorizationError(authorizationRequest, e.toAuthorizationError())
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
    ) {
        val externalState = externalAuthorizationRequest
            ?.substringAfter("?", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.let { parseQueryParameters(it)["state"]?.singleOrNull() }
            ?: throw IllegalArgumentException("Missing state in external authorization request")

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
            is AuthorizationRequestResult.Success -> result.request.withIssuer(metadataService.issuerBaseUrl())
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

    suspend fun processTokenRequest(
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>> = emptyMap(),
    ): AccessTokenResponseHttp {
        val accessTokenRequest = when (val result = oauth2Provider.createAccessTokenRequest(
            parameters = parameters,
            headers = headers,
            tokenEndpointUri = endpointUri(TOKEN_ENDPOINT_PATH),
        )) {
            is AccessTokenRequestResult.Success -> result.request
            is AccessTokenRequestResult.Failure -> return oauth2Provider.writeAccessTokenError(result.error)
        }.withIssuer(metadataService.issuerBaseUrl())

        val (updatedAccessTokenRequest, response) = when (val result =
            oauth2Provider.createAccessTokenResponse(accessTokenRequest)) {
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
        val session = sessionService.getSession(sessionId)
        notificationService.notify(
            session = session,
            event = IssuanceSessionEvent.requested_token,
        )

        return oauth2Provider.writeAccessTokenResponse(updatedAccessTokenRequest, response)
    }

    suspend fun processCredentialRequest(
        authorizationHeaders: List<String>,
        dpopProofHeaderValues: List<String>,
        parameters: JsonObject,
    ): CredentialResponseHttp {
        val authorization = parseCredentialAuthorization(authorizationHeaders)
            ?: return invalidCredentialAuthorization()
        val parameterMap = parameters.toParametersMap()
        return processCredentialRequest(authorization.token) {
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
    ): CredentialResponseHttp {
        val authorization = parseCredentialAuthorization(authorizationHeaders)
            ?: return invalidCredentialAuthorization()
        return processCredentialRequest(authorization.token) {
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

    private fun invalidCredentialAuthorization(): CredentialResponseHttp =
        oauth2Provider.writeCredentialError(
            OAuthError(OAuthErrorCodes.INVALID_TOKEN, "Credential request has invalid authorization credentials"),
        )

    private suspend fun processCredentialRequest(
        accessToken: String,
        createCredentialRequest: suspend () -> CredentialRequestResult,
    ): CredentialResponseHttp {
        val credentialRequest = when (
            val result = createCredentialRequest()
        ) {
            is CredentialRequestResult.Success -> result.request
            is CredentialRequestResult.Failure -> return oauth2Provider.writeCredentialError(result.error)
            is CredentialRequestResult.OAuthFailure -> return oauth2Provider.writeCredentialError(result.error)
        }
        val tokenClaims = try {
            accessToken.decodeJws().payload
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return oauth2Provider.writeCredentialError(
                credentialRequest,
                OAuthError(OAuthErrorCodes.INVALID_TOKEN, e.message)
            )
        }

        val sessionId = tokenClaims.stringClaim("sub")
            ?: return oauth2Provider.writeCredentialError(
                credentialRequest,
                OAuthError(OAuthErrorCodes.INVALID_TOKEN, "Access token has no session id"),
            )
        val session = try {
            sessionService.getSession(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return oauth2Provider.writeCredentialError(
                credentialRequest.withSession(DefaultSession(subject = sessionId)),
                OAuthError(OAuthErrorCodes.INVALID_TOKEN, e.message),
            )
        }
        val issuerId = session.issuerDid ?: metadataService.issuerBaseUrl()
        val requestWithSession = credentialRequest
            .withSession(DefaultSession(subject = sessionId))
            .withIssuer(issuerId)

        val credentialConfigurationId = when (
            val resolution = credentialRequest.resolveCredentialConfigurationId(
                credentialConfigurationExists = { metadataService.getCredentialConfiguration(it) != null },
                resolveCredentialIdentifier = { identifier ->
                    session.credentialConfigurationId.takeIf { it == identifier }
                },
            )
        ) {
            is CredentialRequestTargetResolution.Success -> resolution.credentialConfigurationId
            is CredentialRequestTargetResolution.Failure -> {
                return failCredentialRequest(requestWithSession, session, resolution.error)
            }
        }

        if (session.credentialConfigurationId != credentialConfigurationId) {
            return failCredentialRequest(
                requestWithSession,
                session,
                CredentialError(
                    CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                    "Credential request references $credentialConfigurationId, but session ${session.sessionId} is for ${session.credentialConfigurationId}",
                ),
            )
        }

        val configuration = metadataService.getCredentialConfiguration(credentialConfigurationId)
            ?: return failCredentialRequest(
                requestWithSession,
                session,
                CredentialError(
                    CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
                    "Unsupported credential_configuration_id: $credentialConfigurationId",
                ),
            )
        val issuerKey = try {
            KeyManager.resolveSerializedKey(session.issuerKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failCredentialRequest(requestWithSession, session, e.toCredentialServerError())
        }
        val x5Chain = try {
            session.x5Chain?.map { id.walt.x509.CertificateDer.fromPEMEncodedString(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failCredentialRequest(requestWithSession, session, e.toCredentialServerError())
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
        val nonceBinding = credentialNonceBinding()

        val credentialResponse = try {
            when (val result = oauth2Provider.createCredentialResponse(
                request = requestWithSession,
                configuration = configuration,
                issuerKey = issuerKey,
                issuerId = issuerId,
                credentialData = credentialDataWithStatus,
                dataMapping = session.mapping,
                selectiveDisclosure = session.selectiveDisclosure,
                x5Chain = x5Chain,
                mDocNameSpacesDataMappingConfig = session.mDocNameSpacesDataMappingConfig,
                credentialStatus = mDocStatus,
                proofValidationContext = CredentialProofValidationContext(
                    credentialIssuer = nonceBinding.credentialIssuer,
                    clientId = requestWithSession.accessTokenClientId,
                    anonymousPreAuthorizedAccess = requestWithSession.anonymousPreAuthorizedAccess,
                    nonceValidation = CredentialNonceValidationContext(
                        service = credentialNonceService,
                        binding = nonceBinding,
                    ),
                ),
            )) {
                is CredentialResponseResult.Success -> result.response
                is CredentialResponseResult.Failure -> {
                    return failCredentialRequest(requestWithSession, session, result.error)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failCredentialRequest(requestWithSession, session, e.toCredentialServerError())
        }

        val issuedCredential = credentialResponse.credentials
            ?.firstOrNull()
            ?.credential
            ?.jsonPrimitive
            ?.contentOrNull
        if (issuedCredential != null) {
            emitCredentialIssuedEvent(
                session = session,
                format = configuration.format,
            )
        }

        val updatedSession = sessionService.updateStatus(
            session.sessionId,
            IssuanceSessionStatus.SUCCESSFUL,
            "Credential issued successfully",
            issuedCredentialFormat = configuration.format.value,
            close = true,
        )
        notificationService.emitIssuanceStatus(updatedSession)

        return oauth2Provider.writeCredentialResponse(requestWithSession, credentialResponse)
    }

    suspend fun createNonceResponse(): IssuedCredentialNonce =
        credentialNonceService.issue(credentialNonceBinding())

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

    private suspend fun failCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        error: CredentialError,
    ): CredentialResponseHttp {
        val updatedSession = sessionService.updateStatus(
            session.sessionId,
            IssuanceSessionStatus.UNSUCCESSFUL,
            error.description ?: error.error,
            close = true,
        )
        notificationService.emitIssuanceStatus(updatedSession)
        return oauth2Provider.writeCredentialError(request, error)
    }

    private suspend fun failCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        error: OAuthError,
    ): CredentialResponseHttp {
        val updatedSession = sessionService.updateStatus(
            session.sessionId,
            IssuanceSessionStatus.UNSUCCESSFUL,
            error.description ?: error.error,
            close = true,
        )
        notificationService.emitIssuanceStatus(updatedSession)
        return oauth2Provider.writeCredentialError(request, error)
    }

    private suspend fun emitCredentialIssuedEvent(
        session: IssuanceSession,
        format: CredentialFormat,
    ) {
        when (format) {
            CredentialFormat.SD_JWT_VC ->
                notificationService.notify(
                    session = session,
                    event = IssuanceSessionEvent.sdjwt_issue,
                )

            CredentialFormat.JWT_VC_JSON,
            CredentialFormat.JWT_VC,
            CredentialFormat.JWT_VC_JSON_LD ->
                notificationService.notify(
                    session = session,
                    event = IssuanceSessionEvent.jwt_issue,
                )

            CredentialFormat.MSO_MDOC ->
                notificationService.notify(
                    session = session,
                    event = IssuanceSessionEvent.generated_mdoc,
                )

            CredentialFormat.LDP_VC -> Unit
        }
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

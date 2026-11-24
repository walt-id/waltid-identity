package id.walt.issuer2.service.openid4vci

import kotlin.time.Duration.Companion.seconds
import id.walt.openid4vci.deferred.*
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.issuer2.config.Issuer2EndpointPaths
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionFailure
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.domain.IssuanceRequest
import id.walt.issuer2.domain.IssuanceResult
import id.walt.issuer2.domain.CredentialStatusConfiguration
import id.walt.issuer2.domain.CredentialStatusIssuanceOperation
import id.walt.issuer2.repository.CredentialStatusConsumptionRepository
import id.walt.issuer2.repository.CredentialStatusCapacityException
import id.walt.issuer2.repository.CredentialStatusOperationException
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.utils.JsonObjectPathMapper
import id.walt.openid4vci.clientauth.attestation.challenge.AttestationChallengeService
import id.walt.openid4vci.responses.challenge.AttestationChallengeResponse
import id.walt.openid4vci.responses.challenge.AttestationChallengeResponseHttp
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.errors.CredentialProtocolException
import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.NotificationErrorCodes
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInput
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInputProvider
import id.walt.openid4vci.mdoc.MsoValidityResolver
import id.walt.credentials.issuance.InstantClock
import id.walt.credentials.issuance.IssuanceClock
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.toDefaultAuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.walt.openid4vci.requests.authorization.OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE
import id.walt.openid4vci.requests.credential.CredentialAuthorization
import id.walt.openid4vci.requests.credential.CredentialAuthorizationResolution
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.credential.resolveCredentialAuthorization
import id.walt.openid4vci.requests.credential.toAuthorizationDetails
import id.walt.openid4vci.requests.notification.NotificationEvent
import id.walt.openid4vci.requests.notification.NotificationRequestResult
import id.walt.openid4vci.requests.notification.IssuedCredentialNotification
import id.walt.openid4vci.requests.notification.notificationRequestParameters
import id.walt.openid4vci.responses.notification.NotificationResponseResult
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.proofs.CredentialNonceBinding
import id.walt.openid4vci.proofs.CredentialNonceService
import id.walt.openid4vci.proofs.CredentialNonceValidationContext
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationOptions
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.openid4vci.proofs.CredentialProofVerifier
import id.walt.openid4vci.proofs.CredentialProofServiceException
import id.walt.openid4vci.proofs.VerifiedCredentialBinding
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.notification.NotificationResponseHttp
import id.walt.openid4vci.responses.nonce.NonceResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.TokenResponseOptions
import id.walt.openid4vci.responses.token.InvalidTokenCredentialAuthorization
import id.walt.openid4vci.responses.token.TokenCredentialAuthorization
import id.walt.openid4vci.responses.token.resolveTokenCredentialAuthorization
import id.walt.openid4vci.responses.token.authorizedByVerifiedToken
import id.walt.openid4vci.tokens.access.AccessTokenAuthorizationScheme
import id.walt.openid4vci.tokens.access.ProtectedResourceAccessTokenContext
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

private const val INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER = "_issuer2_session_id"
private const val INTERNAL_AUTHORIZATION_REQUEST_ID_PARAMETER = "_issuer2_request_id"
private const val TOKEN_ENDPOINT_PATH = "token"
private const val CREDENTIAL_ENDPOINT_PATH = "credential"

private fun NotificationEvent.toIssuanceSessionEvent(): IssuanceSessionEvent = when (this) {
    NotificationEvent.CREDENTIAL_ACCEPTED -> IssuanceSessionEvent.WALLET_CREDENTIAL_ACCEPTED
    NotificationEvent.CREDENTIAL_FAILURE -> IssuanceSessionEvent.WALLET_CREDENTIAL_FAILURE
    NotificationEvent.CREDENTIAL_DELETED -> IssuanceSessionEvent.WALLET_CREDENTIAL_DELETED
}

private val AUTHORIZATION_CODE_SESSION_LIFETIME = 5.minutes

internal suspend fun restoreSessionIssuerCrypto2Key(
    issuanceRequest: IssuanceRequest,
    runtime: CryptoRuntime,
): Crypto2Key? {
    val encoded = issuanceRequest.crypto2IssuerStoredKey
    if (encoded == null) {
        require(issuanceRequest.issuerKey["type"]?.jsonPrimitive?.content != "jwk") {
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
    /** Vetoes the ordered credential proof keys before the credential batch is constructed. */
    private val credentialProofKeyAcceptance: CredentialProofKeyAcceptance? = null,
    /** Atomically commits proof-key side effects after the complete credential batch was constructed. */
    private val credentialProofKeyCommitment: CredentialProofKeyCommitment? = null,
    /** An optional, additional holder-key policy for hosts that previously supplied a separate verifier. */
    private val credentialProofVerifier: CredentialProofVerifier? = null,
    private val keyAttestation: KeyAttestationVerificationOptions? = null,
    private val attestationChallengeService: AttestationChallengeService? = null,
    private val deferredConfig: DeferredIssuanceConfig = DeferredIssuanceConfig(),
    private val credentialStatusConsumption: CredentialStatusConsumptionRepository? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val deferredIssuance = DeferredIssuance(sessionService.deferredStore, deferredConfig)
    private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    suspend fun processChallengeRequest(requestId: String): AttestationChallengeResponseHttp {
        val service = attestationChallengeService
            ?: throw NotFoundException("Attestation challenges are disabled")
        return try {
            AttestationChallengeResponse(service.issue(metadataService.issuerBaseUrl())).toHttp()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Attestation challenge issuance failed (requestId=$requestId)" }
            AttestationChallengeResponseHttp(
                payload = buildJsonObject {
                    put("error", OAuthErrorCodes.SERVER_ERROR)
                    put("error_description", "Client attestation challenge service is unavailable")
                },
                status = 500,
            )
        }
    }

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
                    oauth2Provider.writePushedAuthorizationError(
                        parRequest,
                        responseResult.error
                    ) to responseResult.error
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
            resolvedParameters.withInternalAuthorizationSession(issuanceSession.sessionId) +
                    (INTERNAL_AUTHORIZATION_REQUEST_ID_PARAMETER to listOf(authorizationRequest.id))
        val authorizationRequestEnvelope = try {
            internalAuthorizationRequest.encodeExternalLoginAuthorizationParameters().also {
                sessionService.saveSession(
                    issuanceSession.copy(
                        authorizationRequest = authorizationRequest.toDefaultAuthorizationRequest(),
                        externalAuthorizationState = null,
                    )
                )
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

    internal suspend fun resolveExternalLoginSession(envelope: String): IssuanceSession {
        val parameters = envelope.decodeExternalLoginAuthorizationParameters()
        val sessionId = parameters[INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER]?.singleOrNull()
            ?: throw IllegalArgumentException("Missing issuance session id in internal authorization request")
        val session = requireNotNull(sessionService.getSessionOrNull(sessionId)) { "Invalid login reference" }
        require(session.isActiveAuthorizationCodeSession()) { "Expired or invalid login reference" }
        val stored = requireNotNull(session.authorizationRequest) { "Missing stored authorization request" }
        require(
            parameters[INTERNAL_AUTHORIZATION_REQUEST_ID_PARAMETER]?.singleOrNull() == stored.id &&
                    parameters.withoutInternalAuthorizationSession() == stored.requestForm.withoutInternalAuthorizationSession()
        ) {
            "Authorization parameters do not match the accepted request"
        }
        return session
    }

    suspend fun processExternalLoginInterception(
        externalAuthorizationRequest: String?,
        authorizationRequestEnvelope: String?,
        requestId: String,
    ) {
        val session = try {
            val session = resolveExternalLoginSession(requireNotNull(authorizationRequestEnvelope))
            require(session.externalAuthorizationState == null) { "Login reference has already been used" }
            session
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

        val storedAuthorizationRequest = session.authorizationRequest ?: run {
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

        val authorizationRequest = storedAuthorizationRequest.withIssuer(metadataService.issuerBaseUrl())

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

        val issuanceRequests = try {
            session.issuanceRequests.map { issuanceRequest ->
                issuanceRequest.copy(
                    credentialData = issuanceRequest.idTokenClaimsMapping?.let { claimsMapping ->
                        JsonObjectPathMapper.fromSourceToDestinationJsonPathsMap(
                            source = idTokenClaims,
                            destination = issuanceRequest.credentialData,
                            jsonPathMapConfig = claimsMapping,
                        )
                    } ?: issuanceRequest.credentialData
                )
            }
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
            issuanceRequests = issuanceRequests,
            authorizationClaims = idTokenClaims,
            externalAuthorizationState = null,
        )

        return createAuthorizationResponse(
            issuanceSession = updatedSession,
            authorizationRequest = authorizationRequest,
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
            oauth2Provider.createAccessTokenResponse(
                accessTokenRequest,
                TokenResponseOptions(
                    credentialAuthorizationResolver = { updatedAccessTokenRequest, refreshGrant ->
                        tokenCredentialAuthorization(updatedAccessTokenRequest, refreshGrant)
                    },
                ),
            )
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

                result.credentialAuthorization?.issuanceDeadline?.let { deferredIssuance.start(sessionId, it) }
                val authorizedSession = sessionService.establishIssuanceSelection(
                    session.sessionId,
                    requireNotNull(result.credentialAuthorization).credentialIdentifiers
                )
                val response = oauth2Provider.writeAccessTokenResponse(result.request, result.response)
                notificationService.notify(
                    requestId = requestId,
                    session = authorizedSession,
                    event = event,
                )
                response
            }
        }
    }

    private suspend fun tokenCredentialAuthorization(
        accessTokenRequest: AccessTokenRequest,
        refreshGrant: List<AuthorizationDetail>?,
    ): TokenCredentialAuthorization {
        val sessionId = requireNotNull(accessTokenRequest.session?.subject?.takeIf(String::isNotBlank)) {
            "Token request has no issuance session"
        }
        val session = sessionService.getSession(sessionId)
        if (session.isClosed && !(session.deferredStatus == DeferredStatus.DENIED && refreshGrant != null)) {
            throw InvalidTokenCredentialAuthorization(
                accessTokenRequest,
                "Issuance session is closed; restart authorization"
            )
        }
        val authorizationConfigurations = session.authorizationRequest?.requestForm?.let { parameters ->
            credentialConfigurationIdsFromAuthorizationDetails(parameters) +
                    metadataService.credentialConfigurationIdsForScopes(
                        parameters["scope"].orEmpty().flatMap { it.split(' ') }.filter(String::isNotBlank).toSet()
                    )
        }.orEmpty()
        val includeDetails = accessTokenRequest.authorizationDetails.any {
            it.type == OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE
        } || session.authorizationRequest?.requestForm?.let(::credentialConfigurationIdsFromAuthorizationDetails)
            ?.isNotEmpty() == true
        return resolveTokenCredentialAuthorization(
            request = accessTokenRequest,
            candidates = session.issuanceRequests.map { it.toCredentialAuthorization() },
            grantConfigurationIds = authorizationConfigurations.ifEmpty {
                session.issuanceRequests.map { it.credentialConfigurationId }.toSet()
            },
            tokenScopeConfigurationIds = metadataService.credentialConfigurationIdsForScopes(
                accessTokenRequest.grantedScopes + accessTokenRequest.requestedScopes
            ),
            establishedSelection = session.authorizedCredentialIdentifiers,
            refreshGrant = refreshGrant,
            includeInResponse = includeDetails,
        ).copy(issuanceDeadline = session.deferredState?.let {
            it.deadline ?: (Clock.System.now() + deferredConfig.lifetimeSeconds.seconds)
        })
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

    suspend fun processCredentialRequest(
        authorizationHeaders: List<String>,
        dpopProofHeaderValues: List<String>,
        parameters: JsonObject,
        requestId: String,
    ): CredentialResponseHttp {
        val authorization = parseCredentialAuthorization(authorizationHeaders)
            ?: return invalidCredentialAuthorization(requestId)
        val parameterMap = parameters.toParametersMap()
        return withCredentialErrorHandling(requestId) {
            processCredentialRequest(authorization.token, requestId) {
                oauth2Provider.createCredentialRequest(
                    parameters = parameterMap,
                    accessTokenContext = ProtectedResourceAccessTokenContext(
                        authorization = authorization,
                        expectedIssuer = metadataService.issuerBaseUrl(),
                        dpopProofHeaderValues = dpopProofHeaderValues,
                        targetUri = endpointUri(CREDENTIAL_ENDPOINT_PATH),
                    ),
                )
            }
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
        return withCredentialErrorHandling(requestId) {
            processCredentialRequest(authorization.token, requestId) {
                oauth2Provider.createCredentialRequest(
                    encryptedCredentialRequest = encryptedCredentialRequest,
                    accessTokenContext = ProtectedResourceAccessTokenContext(
                        authorization = authorization,
                        expectedIssuer = metadataService.issuerBaseUrl(),
                        dpopProofHeaderValues = dpopProofHeaderValues,
                        targetUri = endpointUri(CREDENTIAL_ENDPOINT_PATH),
                    ),
                )
            }
        }
    }

    suspend fun processDeferredCredentialRequest(
        authorizationHeaders: List<String>, dpopProofHeaderValues: List<String>,
        parameters: JsonObject? = null, encryptedRequest: String? = null, requestId: String,
    ): CredentialResponseHttp {
        val authorization = parseCredentialAuthorization(authorizationHeaders)
            ?: return invalidCredentialAuthorization(requestId)
        val context = ProtectedResourceAccessTokenContext(
            authorization = authorization, expectedIssuer = metadataService.issuerBaseUrl(),
            dpopProofHeaderValues = dpopProofHeaderValues,
            targetUri = endpointUri("deferred_credential"),
        )
        val result = if (encryptedRequest != null) oauth2Provider.createDeferredCredentialRequest(encryptedRequest, context)
            else oauth2Provider.createDeferredCredentialRequest(requireNotNull(parameters).toParametersMap(), context)
        val request = when (result) {
            is DeferredCredentialRequestResult.Success -> result.request
            is DeferredCredentialRequestResult.Failure -> return oauth2Provider.writeDeferredCredentialError(result.error)
            is DeferredCredentialRequestResult.OAuthFailure -> return oauth2Provider.writeDeferredCredentialError(result.error)
        }
        val claims = authorization.token.decodeJws().payload
        return try {
            val session = claims.stringClaim("sub")?.let { sessionService.getSessionOrNull(it) }
                ?: return oauth2Provider.writeDeferredCredentialError(CredentialError(CredentialErrorCodes.INVALID_TRANSACTION_ID))
            collectDeferredCredential(
                authorization.token, session, request, claims, requestId,
                capacityErrorCode = CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: CredentialProtocolException) {
            oauth2Provider.writeDeferredCredentialError(e.error)
        } catch (e: Exception) {
            credentialIssuanceFailed(null, null, requestId, e)
        }
    }

    private suspend fun collectDeferredCredential(
        accessToken: String, session: IssuanceSession, request: DeferredCredentialRequest,
        tokenClaims: JsonObject, requestId: String, capacityErrorCode: String,
    ): CredentialResponseHttp {
        val authorized = session.issuanceRequests.map { it.toCredentialAuthorization() }.authorizedByVerifiedToken(
            tokenClaims, session.authorizedCredentialIdentifiers,
            metadataService.credentialConfigurationIdsForScopes(tokenClaims.stringClaim("scope").orEmpty().split(' ').filter(String::isNotBlank).toSet()),
        ).map { it.credentialIdentifier }.toSet()
        return when (val collection = deferredIssuance.collect(session.sessionId, request.transactionId, authorized, tokenClaims.stringClaim("client_id")?.takeIf(String::isNotBlank))) {
            is DeferredIssuance.Collection.Pending -> when (
                val creation = oauth2Provider.createDeferredCredentialResponse(request, collection.response)
            ) {
                is CredentialResponseResult.Success -> oauth2Provider.writeDeferredCredentialResponse(request, creation.response)
                is CredentialResponseResult.Failure -> oauth2Provider.writeDeferredCredentialError(creation.error)
            }
            is DeferredIssuance.Collection.Claimed -> {
                val claim = collection.transaction
                try {
                    val prepared = claim.accepted.restore(
                        request.credentialResponseEncryption, DeferredCredentialResponseContext(session.sessionId, claim, deferredIssuance),
                    )
                    val response = try {
                        deferredIssuance.withClaim(session.sessionId, claim) {
                            if (claim.response != null && claim.accepted.credentialIdentifier in sessionService.getSession(session.sessionId).issuanceResults) {
                                when (val result = oauth2Provider.createDeferredCredentialResponse(
                                    request, requireNotNull(claim.response), DeferredCredentialResponseContext(session.sessionId, claim, deferredIssuance),
                                )) {
                                    is CredentialResponseResult.Success -> oauth2Provider.writeCredentialResponse(prepared, result.response)
                                    is CredentialResponseResult.Failure -> oauth2Provider.writeDeferredCredentialError(result.error)
                                }
                            } else processCredentialRequest(accessToken, requestId, claim) { CredentialRequestResult.Success(prepared) }
                        }
                    } catch (e: CredentialStatusCapacityException) {
                        withContext(NonCancellable) { deferredIssuance.discardUnissued(session.sessionId, claim) }
                        return rejectCredentialRequest(
                            prepared, session, claim.accepted.configuration.format, requestId,
                            CredentialError(capacityErrorCode, e.message),
                        )
                    }
                    // Later polls of a denied transaction are rejected before acquiring a claim.
                    if (response.payload["error"]?.jsonPrimitive?.contentOrNull == CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED) {
                        notificationService.notify(
                            requestId = requestId,
                            session = session,
                            event = credentialRequestEvent(claim.accepted.configuration.format, succeeded = false),
                            error = CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED,
                            errorDescription = response.payload["error_description"]?.jsonPrimitive?.contentOrNull,
                        )
                    }
                    withContext(NonCancellable) {
                        if (response.status == 200) deferredIssuance.consume(session.sessionId, claim)
                        else deferredIssuance.release(session.sessionId, claim)
                    }
                    response
                } catch (e: Throwable) {
                    withContext(NonCancellable) { runCatching { deferredIssuance.release(session.sessionId, claim) } }
                    when (e) {
                        is CancellationException, is CredentialProtocolException -> throw e
                        is Exception -> credentialIssuanceFailed(session, claim.accepted.configuration.format, requestId, e)
                        else -> throw e
                    }
                }
            }
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
        deferredClaim: DeferredTransaction? = null,
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
        } catch (_: NotFoundException) {
            val error = OAuthError(OAuthErrorCodes.INVALID_TOKEN, "Issuance session not found")
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
        } catch (e: Exception) {
            return credentialIssuanceFailed(null, null, requestId, e)
        }
        if (deferredClaim == null && observedSession.issuanceClaimId != null) {
            return invalidCredentialAuthorization(requestId)
        }
        val authorization = when (
            val resolution = credentialRequest.resolveCredentialAuthorization(
                observedSession.issuanceRequests.map { it.toCredentialAuthorization() }.authorizedByVerifiedToken(
                    tokenClaims,
                    observedSession.authorizedCredentialIdentifiers,
                    metadataService.credentialConfigurationIdsForScopes(
                        tokenClaims.stringClaim("scope").orEmpty().split(' ').filter(String::isNotBlank).toSet()
                    ),
                )
            )
        ) {
            is CredentialAuthorizationResolution.Success -> resolution.authorization
            is CredentialAuthorizationResolution.Failure -> {
                val requestWithSession = credentialRequest.withSession(DefaultSession(subject = sessionId))
                return rejectCredentialRequest(
                    requestWithSession,
                    observedSession,
                    null,
                    requestId,
                    resolution.error,
                )
            }
        }
        val observedIssuanceRequest = requireNotNull(observedSession.issuanceRequests.find {
            it.credentialIdentifier == authorization.credentialIdentifier
        })
        val issuerId = observedIssuanceRequest.issuerDid ?: metadataService.issuerBaseUrl()
        val requestWithSession = credentialRequest
            .withSession(DefaultSession(subject = sessionId))
            .withIssuer(issuerId)
        val sessionConfiguration = metadataService.getCredentialConfiguration(
            observedIssuanceRequest.credentialConfigurationId
        )

        if (observedSession.deferredStatus == DeferredStatus.DENIED) {
            return oauth2Provider.writeCredentialError(CredentialError(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, "Credential issuance denied"))
        }
        if (observedSession.expiresAt <= Clock.System.now()) {
            return oauth2Provider.writeCredentialError(CredentialError(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, "Issuance session expired"))
        }
        if (
            observedSession.isClosed ||
            observedSession.status !in setOf(IssuanceSessionStatus.ACTIVE, IssuanceSessionStatus.SUCCESSFUL)
        ) {
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
        val configuration = sessionConfiguration ?: return rejectCredentialRequest(
            requestWithSession,
            observedSession,
            null,
            requestId,
            CredentialError(
                CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
                "Unsupported credential_configuration_id: ${observedIssuanceRequest.credentialConfigurationId}",
            ),
        )

        val nonceBinding = credentialNonceBinding()

        val proofValidationContext = CredentialProofValidationContext(
            keyAttestation = keyAttestation,
            credentialIssuer = nonceBinding.credentialIssuer,
            clientId = requestWithSession.accessTokenClientId,
            anonymousPreAuthorizedAccess = requestWithSession.anonymousPreAuthorizedAccess,
            nonceValidation = CredentialNonceValidationContext(
                service = credentialNonceService,
                binding = nonceBinding,
            ),
            batchCredentialIssuance = metadataService.getCredentialIssuerMetadata().batchCredentialIssuance,
        )

        if (observedSession.deferredState != null && deferredClaim == null) {
            try {
                val accepted = try {
                    oauth2Provider.acceptCredentialRequest(
                        requestWithSession, configuration, authorization.credentialIdentifier,
                        authorization.credentialConfigurationId,
                        proofValidationContext,
                    )
                } catch (e: CredentialProtocolException) {
                    // Collection notifications apply only after a transaction has been accepted.
                    if (e.error.error == CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED) {
                        return rejectCredentialRequest(requestWithSession, observedSession, configuration.format, requestId, e.error)
                    }
                    throw e
                }
                val publicKeys = accepted.bindings.map { it.publicJwk }
                validateExpectedCredentialProofKeys(publicKeys, observedIssuanceRequest)?.let {
                    return oauth2Provider.writeCredentialError(it)
                }
                credentialProofKeyAcceptance?.let {
                    if (!it.accept(observedSession, publicKeys)) {
                        return oauth2Provider.writeCredentialError(CredentialError(CredentialErrorCodes.INVALID_PROOF, "Credential proof key was not accepted"))
                    }
                }
                val transaction = deferredIssuance.accept(sessionId, accepted)
                return collectDeferredCredential(
                    accessToken, observedSession, DeferredCredentialRequest(transaction.id, requestWithSession.credentialResponseEncryption),
                    tokenClaims, requestId,
                    capacityErrorCode = CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                )
            } catch (e: CredentialProofValidationException) {
                val error = CredentialError(e.errorCode, e.message)
                if (error.error == CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED) {
                    return rejectCredentialRequest(requestWithSession, observedSession, configuration.format, requestId, error)
                }
                return oauth2Provider.writeCredentialError(error)
            } catch (e: CredentialProtocolException) {
                return oauth2Provider.writeDeferredCredentialError(e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return credentialIssuanceFailed(observedSession, configuration.format, requestId, e)
            }
        }

        // Preallocated status requires a durable lease so a crashed process cannot lose the session.
        val session = try {
            when {
                deferredClaim != null -> observedSession
                observedSession.requiresCredentialStatusLease -> sessionService.claimCredentialStatusSession(sessionId)
                else -> sessionService.claimSession(sessionId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return credentialIssuanceFailed(observedSession, configuration.format, requestId, e)
        }
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

        val issuanceRequest = session.issuanceRequests.find {
            it.credentialIdentifier == authorization.credentialIdentifier
        }
        if (issuanceRequest == null || session.authorizedCredentialIdentifiers?.let {
                authorization.credentialIdentifier !in it
            } == true) {
            restoreClaimedSession(session)
            return rejectCredentialRequest(
                requestWithSession,
                session,
                configuration.format,
                requestId,
                CredentialError(
                    CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
                    "Credential is unavailable",
                ),
            )
        }

        var claimFinalized = false
        var committedProofKeyJwks: List<JsonObject>? = null
        try {
            val issuerKey = KeyManager.resolveSerializedKey(issuanceRequest.issuerKey)
            val crypto2IssuerKey = restoreSessionIssuerCrypto2Key(issuanceRequest, crypto2Runtime)
            val x5Chain = issuanceRequest.x5Chain?.map { X509CertificateUtil.parseCertificatePem(it) }
            val statusConfiguration = CredentialStatusConfiguration.parse(
                issuanceRequest.credentialStatus, configuration.format, issuanceRequest.mapping,
            )
            val usesPreallocatedStatus = statusConfiguration is CredentialStatusConfiguration.Preallocated
            var statusOperation: CredentialStatusIssuanceOperation? = null
            var statusHolderKeys = emptyList<String>()
            var proofPublicKeyJwks: List<JsonObject>? = null
            val issuedAt = Clock.System.now()
            val resolvedMsoValidity = if (configuration.format == CredentialFormat.MSO_MDOC) {
                MsoValidityResolver.resolve(
                    issuanceRequest.msoData,
                    signed = issuedAt,
                    clock = InstantClock(issuedAt),
                )
            } else {
                require(issuanceRequest.msoData == null || issuanceRequest.msoData.isEmpty()) {
                    "msoData is only supported for mso_mdoc credentials"
                }
                null
            }

            val issuanceInputData = object : CredentialIssuanceInputProvider {
                override suspend fun prepare(bindings: List<VerifiedCredentialBinding>): CredentialResponseResult? {
                    val retainProofKeys = usesPreallocatedStatus || requiresCredentialProofKeys(issuanceRequest)
                    val additionalVerifier = credentialProofVerifier.takeIf { deferredClaim == null }
                    if (retainProofKeys || (additionalVerifier != null && bindings.size > 1)) {
                        if (bindings.isEmpty()) return CredentialResponseResult.Failure(
                            CredentialError(CredentialErrorCodes.INVALID_PROOF, "Credential request has no credential proof"),
                        )
                        val publicKeys = bindings.map { it.holderKey.exportPublicJwkObject() }
                        if (additionalVerifier != null) {
                            validateAdditionalProofKeys(additionalVerifier, requestWithSession, configuration,
                                proofValidationContext, publicKeys)?.let { return CredentialResponseResult.Failure(it) }
                        }
                        if (retainProofKeys) proofPublicKeyJwks = publicKeys
                        validateExpectedCredentialProofKeys(publicKeys, issuanceRequest)?.let {
                            return CredentialResponseResult.Failure(it)
                        }
                        checkCredentialProofKeyAcceptance(session, publicKeys)?.let { return CredentialResponseResult.Failure(it) }
                        if (usesPreallocatedStatus) {
                            statusHolderKeys = bindings.mapIndexed { index, binding ->
                                credentialStatusBinding(publicKeys[index], binding)
                            }
                            if (deferredClaim == null) statusOperation = sessionService.prepareCredentialStatusOperation(
                                session, issuanceRequest.credentialIdentifier, statusHolderKeys,
                            )
                        }
                    }
                    return (deferredClaim?.response ?: statusOperation?.response)?.let { CredentialResponseResult.Success(it) }
                }

                override suspend fun provide(credentialCount: Int): List<CredentialIssuanceInput> = when (statusConfiguration) {
                    is CredentialStatusConfiguration.Fixed -> List(credentialCount) {
                        createCredentialIssuanceInput(issuanceRequest, configuration.format, statusConfiguration.value)
                    }
                    is CredentialStatusConfiguration.Preallocated -> {
                        val repository = checkNotNull(credentialStatusConsumption) { "Per-copy credential status requires a status repository" }
                        val groups = repository.reserve(
                            profileId = issuanceRequest.profileId,
                            operationId = deferredClaim?.id ?: requireNotNull(statusOperation).id,
                            holderKeys = statusHolderKeys,
                            groups = statusConfiguration.groups,
                            expiresAt = session.deferredState?.deadline ?: session.expiresAt,
                        )
                        groups.map { group ->
                            val value = CredentialStatusConfiguration.credentialValue(group, configuration.format)
                            if (configuration.format == CredentialFormat.MSO_MDOC) {
                                val status = MdocStatus(statusList = CredentialStatusConfiguration.tokenStatusList(value))
                                CredentialIssuanceInput(issuanceRequest.credentialData, status)
                            } else {
                                createCredentialIssuanceInput(issuanceRequest, configuration.format, value)
                            }
                        }
                    }
                }
            }
            val notificationId = UUID.randomUUID().toString()
            val credentialResponseResult = withContext(IssuanceClock(issuedAt)) {
                if (crypto2IssuerKey != null) {
                    oauth2Provider.createCredentialResponse(
                        request = requestWithSession,
                        configuration = configuration,
                        issuerKey = Crypto2CredentialSigningKey.select(crypto2IssuerKey, configuration),
                        issuerId = issuerId,
                        issuanceInputData = issuanceInputData,
                        dataMapping = issuanceRequest.mapping,
                        selectiveDisclosure = issuanceRequest.selectiveDisclosure,
                        x5Chain = x5Chain,
                        mDocNameSpacesDataMappingConfig = issuanceRequest.mDocNameSpacesDataMappingConfig,
                        authorizedTransactionDataTypes = issuanceRequest.authorizedTransactionDataTypes,
                        validFrom = resolvedMsoValidity?.validFrom,
                        validUntil = resolvedMsoValidity?.validUntil,
                        expectedUpdate = resolvedMsoValidity?.expectedUpdate,
                        proofValidationContext = proofValidationContext,
                        notificationId = notificationId,
                    )
                } else {
                    oauth2Provider.createCredentialResponse(
                        request = requestWithSession,
                        configuration = configuration,
                        issuerKey = issuerKey,
                        issuerId = issuerId,
                        issuanceInputData = issuanceInputData,
                        dataMapping = issuanceRequest.mapping,
                        selectiveDisclosure = issuanceRequest.selectiveDisclosure,
                        x5Chain = x5Chain,
                        mDocNameSpacesDataMappingConfig = issuanceRequest.mDocNameSpacesDataMappingConfig,
                        authorizedTransactionDataTypes = issuanceRequest.authorizedTransactionDataTypes,
                        validFrom = resolvedMsoValidity?.validFrom,
                        validUntil = resolvedMsoValidity?.validUntil,
                        expectedUpdate = resolvedMsoValidity?.expectedUpdate,
                        proofValidationContext = proofValidationContext,
                        notificationId = notificationId,
                    )
                }
            }
            val credentialResponse = when (credentialResponseResult) {
                is CredentialResponseResult.Success -> credentialResponseResult.response
                is CredentialResponseResult.Failure -> {
                    val denied = credentialResponseResult.error.error == CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED
                    if (deferredClaim != null && (denied || credentialResponseResult.error.retryAfterSeconds != null)) {
                        if (denied) withContext(NonCancellable) { deferredIssuance.deny(sessionId, deferredClaim) }
                        return oauth2Provider.writeDeferredCredentialError(credentialResponseResult.error)
                    }
                    val response = if (credentialResponseResult.error.isRetryableProofFailure()) {
                        retryCredentialRequest(
                            requestWithSession,
                            session,
                            configuration.format,
                            requestId,
                            credentialResponseResult.error,
                        )
                    } else {
                        failClaimedCredentialRequest(
                            requestWithSession,
                            session,
                            configuration.format,
                            requestId,
                            credentialResponseResult.error,
                        )
                    }
                    claimFinalized = true
                    return response
                }
            }

            if (credentialResponse.isDeferred) {
                if (deferredClaim == null) {
                    restoreClaimedSession(session)
                    claimFinalized = true
                }
                return oauth2Provider.writeCredentialResponse(requestWithSession, credentialResponse)
            }
            if (deferredClaim != null && deferredClaim.response == null) {
                deferredIssuance.checkpoint(sessionId, deferredClaim, credentialResponse)
            }
            statusOperation?.takeIf { it.response == null }?.let { operation ->
                sessionService.checkpointCredentialStatusResponse(
                    session, issuanceRequest.credentialIdentifier, operation.id, credentialResponse,
                )
            }

            val responseToWrite = if (deferredClaim == null) credentialResponse else {
                when (val creation = oauth2Provider.createDeferredCredentialResponse(
                    DeferredCredentialRequest(deferredClaim.id, requestWithSession.credentialResponseEncryption), credentialResponse,
                    DeferredCredentialResponseContext(sessionId, deferredClaim, deferredIssuance),
                )) {
                    is CredentialResponseResult.Success -> creation.response
                    is CredentialResponseResult.Failure -> return oauth2Provider.writeDeferredCredentialError(creation.error)
                }
            }

            if (responseToWrite.isDeferred) {
                return oauth2Provider.writeCredentialResponse(requestWithSession, responseToWrite)
            }
            credentialProofKeyCommitment?.let { commitment ->
                val committed = try {
                    commitment.commit(session, requireNotNull(proofPublicKeyJwks))
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
                if (!usesPreallocatedStatus) committedProofKeyJwks = proofPublicKeyJwks
            }

            val response = oauth2Provider.writeCredentialResponse(requestWithSession, responseToWrite)
            if (deferredClaim != null && response.status != 200) {
                // Keep the signed checkpoint for a later poll with valid response encryption.
                claimFinalized = true
                return response
            }
            if (response.status != 200) {
                restoreClaimedSession(session)
                claimFinalized = true
                return response
            }
            val issuedResult = IssuanceResult(
                issuedCredentialFormat = configuration.format.value,
                walletNotification = credentialResponse.notificationId?.let {
                    IssuedCredentialNotification(issuanceRequest.credentialIdentifier, it)
                },
            )
            val issuedResults = mapOf(issuanceRequest.credentialIdentifier to issuedResult)
            fun recordIssuance(current: IssuanceSession): IssuanceSession {
                val results = current.issuanceResults + issuedResults
                val selected = current.authorizedCredentialIdentifiers ?: current.issuanceRequests
                    .map { it.toCredentialAuthorization() }.authorizedByVerifiedToken(
                        tokenClaims, null,
                        metadataService.credentialConfigurationIdsForScopes(
                            tokenClaims.stringClaim("scope").orEmpty().split(' ').filter(String::isNotBlank).toSet()
                        ),
                    ).map { it.credentialIdentifier }
                val complete = selected.isNotEmpty() && selected.all { it in results }
                return current.copy(
                    issuanceResults = results,
                    authorizedCredentialIdentifiers = selected,
                    status = if (complete) IssuanceSessionStatus.SUCCESSFUL else IssuanceSessionStatus.ACTIVE,
                    statusReason = when {
                        current.statusReason != session.statusReason -> current.statusReason
                        complete -> "Credentials issued successfully"
                        else -> null
                    },
                )
            }
            val updatedSession = try {
                withContext(NonCancellable) {
                    when {
                        deferredClaim != null -> sessionService.saveDeferredProgress(recordIssuance(session).copy(issuanceResults = issuedResults))
                        session.credentialStatusIssuance?.leaseId != null -> sessionService.finishCredentialStatusSession(
                            session, issuanceRequest.credentialIdentifier, ::recordIssuance,
                        )
                        else -> sessionService.finishClaimedSession(recordIssuance(session).copy(issuanceResults = issuedResults))
                    }
                }
            } catch (e: Exception) {
                val retrySession = try {
                    restoreClaimedSession(session) { current ->
                        if (statusOperation != null) current else current.withExpectedCredentialProofKeys(
                            issuanceRequest.credentialIdentifier, proofPublicKeyJwks,
                        )
                    }
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
                restoreClaimedSession(session) { current ->
                    current.withExpectedCredentialProofKeys(issuanceRequest.credentialIdentifier, committedProofKeyJwks)
                }
            }
            throw e
        } catch (e: CredentialStatusCapacityException) {
            if (deferredClaim != null) throw e
            return withCredentialErrorHandling(requestId, session, configuration.format) {
                if (session.credentialStatusIssuance?.leaseId != null) {
                    sessionService.discardUnreservedCredentialStatusOperation(session, issuanceRequest.credentialIdentifier)
                }
                retryCredentialRequest(
                    requestWithSession, session, configuration.format, requestId,
                    CredentialError(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, e.message),
                )
            }
        } catch (e: CredentialStatusOperationException) {
            // A stale worker must never overwrite a newer claim while attempting cleanup.
            try {
                restoreClaimedSession(session)
            } catch (_: CredentialStatusOperationException) {
                // The newer owner (or session expiry) controls the persisted state.
            }
            claimFinalized = true
            return rejectCredentialRequest(requestWithSession, session, configuration.format, requestId,
                CredentialError(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, e.message))
        } catch (e: CredentialProofServiceException) {
            logger.error(e) { "Credential proof verification unavailable (requestId=$requestId)" }
            return failClaimedCredentialRequest(requestWithSession, session, configuration.format, requestId,
                OAuthError(OAuthErrorCodes.SERVER_ERROR, "Credential proof verification unavailable"))
        } catch (e: CredentialProtocolException) {
            if (!claimFinalized && deferredClaim == null) restoreClaimedSession(session)
            throw e
        } catch (e: Exception) {
            if (claimFinalized || deferredClaim != null) throw e
            try {
                restoreClaimedSession(session)
            } catch (cleanup: CancellationException) {
                throw cleanup
            } catch (cleanup: Exception) {
                logger.warn { "Could not release issuance claim (requestId=$requestId, exception=${cleanup.javaClass.simpleName})" }
            }
            return credentialIssuanceFailed(session, configuration.format, requestId, e)
        }
    }

    suspend fun processNotificationRequest(
        authorizationHeaders: List<String>,
        dpopProofHeaderValues: List<String>,
        requestBody: String,
        requestId: String,
    ): NotificationResponseHttp {
        val authorization = parseCredentialAuthorization(authorizationHeaders)
            ?: return oauth2Provider.writeNotificationError(
                OAuthError(OAuthErrorCodes.INVALID_TOKEN, "Invalid access token"),
                AccessTokenAuthorizationScheme.BEARER,
            )
        when (
            val result = oauth2Provider.createNotificationRequest(
                parameters = notificationRequestParameters(requestBody),
                accessTokenContext = ProtectedResourceAccessTokenContext(
                    authorization = authorization,
                    expectedIssuer = metadataService.issuerBaseUrl(),
                    dpopProofHeaderValues = dpopProofHeaderValues,
                    targetUri = endpointUri(Issuer2EndpointPaths.NOTIFICATION),
                ),
            )
        ) {
            is NotificationRequestResult.OAuthFailure ->
                return oauth2Provider.writeNotificationError(result.error, authorization.scheme)

            is NotificationRequestResult.Failure ->
                return oauth2Provider.writeNotificationError(result.error)

            is NotificationRequestResult.Success -> {
                val sessionId = result.tokenClaims.stringClaim(JwtPayloadClaims.SUBJECT)
                    ?: return oauth2Provider.writeNotificationError(
                        OAuthError(OAuthErrorCodes.INVALID_TOKEN, "Access token has no session id"),
                        authorization.scheme,
                    )
                var current = sessionService.getSessionOrNull(sessionId)
                    ?: return oauth2Provider.writeNotificationError(
                        NotificationError(NotificationErrorCodes.INVALID_NOTIFICATION_ID),
                    )
                val scopeConfigurationIds = metadataService.credentialConfigurationIdsForScopes(
                    result.tokenClaims.stringClaim("scope").orEmpty()
                        .split(' ')
                        .filter(String::isNotBlank)
                        .toSet(),
                )
                while (true) {
                    val authorizedIdentifiers = current.issuanceRequests
                        .map { it.toCredentialAuthorization() }
                        .authorizedByVerifiedToken(
                            result.tokenClaims,
                            current.authorizedCredentialIdentifiers,
                            scopeConfigurationIds,
                        )
                        .map { it.credentialIdentifier }
                        .toSet()
                    val response = oauth2Provider.createNotificationResponse(
                        request = result.request,
                        storedNotifications = current.issuanceResults.values.mapNotNull { it.walletNotification },
                        authorizedCredentialIdentifiers = authorizedIdentifiers,
                    )
                    val change = (response as? NotificationResponseResult.Success)?.change
                        ?: return oauth2Provider.writeNotificationResponse(response)
                    val saved = sessionService.trySaveWalletNotification(current, change)
                    if (saved != null) {
                        notificationService.notify(
                            requestId = requestId,
                            session = saved,
                            event = change.event.toIssuanceSessionEvent(),
                        )
                        return oauth2Provider.writeNotificationResponse(response)
                    }
                    current = sessionService.getSessionOrNull(sessionId)
                        ?: return oauth2Provider.writeNotificationError(
                            OAuthError(OAuthErrorCodes.TEMPORARILY_UNAVAILABLE, "Issuance session is being processed"),
                            authorization.scheme,
                        )
                }
            }
        }
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
            authorizationRequest = authorizationRequest.toDefaultAuthorizationRequest(),
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
                status in setOf(IssuanceSessionStatus.ACTIVE, IssuanceSessionStatus.SUCCESSFUL) &&
                !isClosed &&
                expiresAt > Clock.System.now()

    private suspend fun createAuthorizationCodeSessionFromProfile(
        authorizationRequest: AuthorizationRequest,
        parameters: Map<String, List<String>>,
    ): IssuanceSession {
        val credentialConfigurationIds = resolveRequestedCredentialConfigurationIds(authorizationRequest, parameters)
        val profiles = credentialConfigurationIds.map(profileService::resolveProfileByCredentialConfigurationId)
        val notifications = profiles.map { it.notifications }.distinct().singleOrNull()
        require(profiles.all { it.notifications == notifications }) {
            "All credentials in an issuance session must resolve to the same notification configuration"
        }
        return sessionService.createSession(
            IssuanceSession(
                sessionId = UUID.randomUUID().toString(),
                authenticationMethod = AuthenticationMethod.AUTHORIZED,
                issuanceRequests = profiles.map(::createIssuanceRequest),
                authorizationRequest = authorizationRequest.toDefaultAuthorizationRequest(),
                expiresAt = Clock.System.now() + AUTHORIZATION_CODE_SESSION_LIFETIME,
                notifications = notifications,
            )
        )
    }

    private fun createIssuanceRequest(profile: CredentialProfile): IssuanceRequest = with(profile) {
        IssuanceRequest(
            credentialIdentifier = UUID.randomUUID().toString(),
            profileId = profileId,
            credentialConfigurationId = credentialConfigurationId,
            issuerKey = issuerKey,
            credentialData = credentialData,
            mapping = mapping,
            selectiveDisclosure = selectiveDisclosure,
            idTokenClaimsMapping = idTokenClaimsMapping,
            mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
            authorizedTransactionDataTypes = authorizedTransactionDataTypes,
            msoData = msoData,
            x5Chain = x5Chain,
            issuerDid = issuerDid,
            credentialStatus = profileService.statusForIssuance(profile),
        )
    }

    private fun resolveRequestedCredentialConfigurationIds(
        authorizationRequest: AuthorizationRequest,
        parameters: Map<String, List<String>>,
    ): List<String> {
        val authorizationDetailsMatches = credentialConfigurationIdsFromAuthorizationDetails(parameters)
        val scopeMatches = metadataService.credentialConfigurationIdsForScopes(authorizationRequest.requestedScopes)
        val matches = authorizationDetailsMatches + scopeMatches

        require(matches.isNotEmpty()) {
            "No credential configuration could be resolved from authorization_details or requested scopes: " +
                    authorizationRequest.requestedScopes
        }
        return matches.toList()
    }

    private fun credentialConfigurationIdsFromAuthorizationDetails(
        parameters: Map<String, List<String>>,
    ): Set<String> =
        parameters["authorization_details"]
            .orEmpty()
            .flatMap { raw -> parseAuthorizationDetails(raw) }
            .filter { detail ->
                detail["type"]?.jsonPrimitive?.contentOrNull == OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE
            }
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

    private fun requiresCredentialProofKeys(issuanceRequest: IssuanceRequest): Boolean =
        credentialProofKeyAcceptance != null ||
                credentialProofKeyCommitment != null ||
                issuanceRequest.expectedCredentialProofKeyJwks != null

    private suspend fun credentialStatusBinding(publicJwk: JsonObject, binding: VerifiedCredentialBinding): String =
        buildJsonObject {
            put("key", publicJwk.thumbprint())
            binding.holderKid?.let { put("kid", it) }
            binding.holderDid?.let { put("did", it) }
            put("proofIndexes", JsonArray(binding.proofIndexes.sorted().map(::JsonPrimitive)))
        }.toString()

    private suspend fun JsonObject.thumbprint(): String =
        Jwk.sha256Thumbprint(EncodedKey.Jwk(BinaryData(toString().encodeToByteArray()), privateMaterial = false))

    private suspend fun validateAdditionalProofKeys(
        verifier: CredentialProofVerifier,
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        context: CredentialProofValidationContext,
        signingKeys: List<JsonObject>,
    ): CredentialError? = try {
        val inspected = verifier.verify(request, configuration, context)
        val keys = inspected.bindings.map { it.holderKey.exportPublicJwkObject().thumbprint() }
        if (keys == signingKeys.map { it.thumbprint() }) null
        else CredentialError(CredentialErrorCodes.INVALID_PROOF, "Credential proof holder keys disagree")
    } catch (e: CancellationException) {
        throw e
    } catch (e: CredentialProofServiceException) {
        throw e
    } catch (e: CredentialProofValidationException) {
        CredentialError(e.errorCode, e.message)
    } catch (e: CredentialProtocolException) {
        e.error
    } catch (e: Exception) {
        e.toCredentialProofError()
    }

    private suspend fun checkCredentialProofKeyAcceptance(session: IssuanceSession, publicKeys: List<JsonObject>): CredentialError? {
        val acceptance = credentialProofKeyAcceptance ?: return null
        return try {
            if (acceptance.accept(session, publicKeys)) null
            else CredentialError(CredentialErrorCodes.INVALID_PROOF, "Credential proof key was not accepted")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toCredentialProofError()
        }
    }

    private suspend fun validateExpectedCredentialProofKeys(
        publicKeys: List<JsonObject>,
        issuanceRequest: IssuanceRequest,
    ): CredentialError? {
        val expectedJwks = issuanceRequest.expectedCredentialProofKeyJwks ?: return null
        return try {
            val presentedThumbprints = publicKeys.map { it.thumbprint() }
            val expectedThumbprints = expectedJwks.map { it.thumbprint() }
            val matches = if (expectedThumbprints.size == 1) {
                presentedThumbprints.isNotEmpty() && presentedThumbprints.all { it == expectedThumbprints.single() }
            } else {
                presentedThumbprints == expectedThumbprints
            }
            if (matches) null else CredentialError(
                CredentialErrorCodes.INVALID_PROOF, "Credential proof key does not match the issuance session",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CredentialError(CredentialErrorCodes.INVALID_PROOF, "Credential proof key could not be compared")
        }
    }

    private fun IssuanceRequest.toCredentialAuthorization() = CredentialAuthorization(
        credentialIdentifier = credentialIdentifier,
        credentialConfigurationId = credentialConfigurationId,
    )

    private fun IssuanceSession.withExpectedCredentialProofKeys(
        credentialIdentifier: String,
        proofPublicKeyJwks: List<JsonObject>?,
    ): IssuanceSession {
        if (proofPublicKeyJwks == null) return this
        return copy(
            issuanceRequests = issuanceRequests.map { request ->
                if (request.credentialIdentifier == credentialIdentifier &&
                    request.expectedCredentialProofKeyJwks == null
                ) {
                    request.copy(expectedCredentialProofKeyJwks = proofPublicKeyJwks)
                } else {
                    request
                }
            }
        )
    }

    /** Reports a rejection without changing the issuance-session lifecycle. */
    private suspend fun rejectCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat?,
        requestId: String,
        error: CredentialError,
    ): CredentialResponseHttp {
        if (error.isRetryableProofFailure()) {
            logger.warn {
                "Credential proof rejected: " + JsonObject(
                    mapOf(
                        "requestId" to JsonPrimitive(requestId),
                        "credential_configuration_id" to JsonPrimitive(request.credentialConfigurationId),
                        "credential_identifier" to JsonPrimitive(request.credentialIdentifier),
                        "error" to JsonPrimitive(error.error),
                        "error_description" to JsonPrimitive(error.description),
                    )
                )
            }
        }
        notificationService.notify(
            requestId = requestId,
            session = session,
            event = credentialRequestEvent(format, succeeded = false),
            error = error.error,
            errorDescription = error.description,
        )
        return oauth2Provider.writeCredentialError(request, error)
    }

    /** Restores a claimed session so a request-level failure does not invalidate sibling credentials. */
    private suspend fun failClaimedCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat,
        requestId: String,
        error: CredentialError,
    ): CredentialResponseHttp {
        return retryCredentialRequest(request, session, format, requestId, error)
    }

    /** Restores a claimed session so a request-level failure does not invalidate sibling credentials. */
    private suspend fun failClaimedCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat,
        requestId: String,
        error: OAuthError,
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

    /** Returns the claimed session unchanged so the wallet can retry the credential request. */
    private suspend fun retryCredentialRequest(
        request: CredentialRequest,
        session: IssuanceSession,
        format: CredentialFormat,
        requestId: String,
        error: CredentialError,
    ): CredentialResponseHttp {
        restoreClaimedSession(session)
        return rejectCredentialRequest(request, session, format, requestId, error)
    }

    private suspend fun restoreClaimedSession(
        session: IssuanceSession,
        update: (IssuanceSession) -> IssuanceSession = { it },
    ): IssuanceSession = withContext(NonCancellable) {
        when {
            session.deferredState != null -> sessionService.saveDeferredProgress(update(session).copy(issuanceResults = emptyMap()))
            session.credentialStatusIssuance?.leaseId != null -> sessionService.releaseCredentialStatusSession(session, update)
            else -> sessionService.finishClaimedSession(update(session).copy(issuanceResults = emptyMap()))
        }
    }

    private fun createCredentialIssuanceInput(
        request: IssuanceRequest,
        format: CredentialFormat,
        status: JsonElement?,
    ): CredentialIssuanceInput {
        val data = if (status == null) request.credentialData else when (format) {
            CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC, CredentialFormat.JWT_VC_JSON_LD ->
                JsonObject(request.credentialData + ("credentialStatus" to status))
            CredentialFormat.SD_JWT_VC -> JsonObject(request.credentialData + ("status" to status))
            else -> request.credentialData
        }
        return CredentialIssuanceInput(data,
            if (format == CredentialFormat.MSO_MDOC && status != null) parseSharedMdocStatus(status) else null)
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
        filterKeys { it != INTERNAL_AUTHORIZATION_SESSION_ID_PARAMETER && it != INTERNAL_AUTHORIZATION_REQUEST_ID_PARAMETER }

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

    private suspend fun withCredentialErrorHandling(
        requestId: String,
        session: IssuanceSession? = null,
        format: CredentialFormat? = null,
        process: suspend () -> CredentialResponseHttp,
    ): CredentialResponseHttp = try {
        process()
    } catch (e: CancellationException) {
        throw e
    } catch (e: CredentialProtocolException) {
        oauth2Provider.writeCredentialError(e.error)
    } catch (e: Exception) {
        credentialIssuanceFailed(session, format, requestId, e)
    }

    private suspend fun credentialIssuanceFailed(
        session: IssuanceSession?,
        format: CredentialFormat?,
        requestId: String,
        cause: Exception,
    ): CredentialResponseHttp {
        logger.error { "Credential issuance failed (requestId=$requestId, exception=${cause.javaClass.simpleName})" }
        val error = OAuthError(OAuthErrorCodes.SERVER_ERROR, "Credential request processing failed")
        notificationService.notify(
            requestId = requestId, session = session, event = credentialRequestEvent(format, succeeded = false),
            error = error.error, errorDescription = error.description,
        )
        return oauth2Provider.writeCredentialError(error)
    }

    private fun parseSharedMdocStatus(status: JsonElement): MdocStatus? {
        return try {
            val statusObj = status.jsonObject
            val statusList = statusObj["status_list"]?.jsonObject
                ?: statusObj["statusList"]?.jsonObject
                ?: return null

            val idx = statusList["idx"]?.jsonPrimitive?.content?.toULongOrNull()
                ?: statusList["index"]?.jsonPrimitive?.content?.toULongOrNull()
                ?: return null
            val uri = statusList["uri"]?.jsonPrimitive?.content
                ?: return null

            MdocStatus(
                statusList = MdocStatusListInfo(
                    index = idx,
                    uri = id.walt.mdoc.objects.mso.UniformResourceIdentifier(uri)
                )
            )
        } catch (_: Exception) {
            null
        }
    }
}

package id.walt.issuer2.service.openid4vci

import id.walt.certificate.x509.X509Certificate
import id.walt.crypto.keys.Key
import id.walt.issuer2.config.IssuanceMode
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.repository.IssuanceSessionCrypto2Keys
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.mdoc.objects.mso.Status
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.Session
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.proofs.CredentialNonceBinding
import id.walt.openid4vci.proofs.CredentialNonceService
import id.walt.openid4vci.proofs.CredentialNonceValidationResult
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.IssuedCredentialNonce
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponse
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseHttp
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.TokenResponseOptions
import id.walt.openid4vci.tokens.access.CredentialAccessTokenContext
import id.walt.sdjwt.SDMap
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DeferredCredentialRequestTest {

    @Test
    fun `deferred credential request returns a pending response for a valid transaction`() = runTest {
        val sessionId = "session-123"
        val session = issuanceSession(sessionId)
        val service = protocolService(session)
        val transactionId = service.registerDeferredCredentialRequest(sessionId = sessionId, intervalSeconds = 7L)

        val response = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject { put("transaction_id", JsonPrimitive(transactionId)) },
            requestId = "request-1",
        )

        assertEquals(202, response.status)
        assertEquals(transactionId, response.payload["transaction_id"]?.jsonPrimitive?.content)
        assertEquals("7", response.payload["interval"]?.jsonPrimitive?.content)
    }

    @Test
    fun `credential request starts deferred when configured`() = runTest {
        val sessionId = "session-456"
        val service = protocolService(issuanceSession(sessionId), IssuanceMode.DEFERRED)

        val response = service.processCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject {},
            requestId = "request-2",
        )

        assertEquals(202, response.status)
        assertEquals("30", response.payload["interval"]?.jsonPrimitive?.content)
        assertEquals("no-store", response.headers["Cache-Control"])

        val deferredResponse = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject {
                put("transaction_id", JsonPrimitive(requireNotNull(response.payload["transaction_id"]?.jsonPrimitive?.content)))
            },
            requestId = "request-2-deferred",
        )
        assertEquals(response.payload["transaction_id"]?.jsonPrimitive?.content, deferredResponse.payload["transaction_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `deferred credential request resolves to a credential after the 30 second interval`() = runTest {
        val sessionId = "session-789"
        val service = protocolService(issuanceSession(sessionId))
        val transactionId = service.registerDeferredCredentialRequest(
            sessionId = sessionId,
            intervalSeconds = 30L,
            requestParameters = buildJsonObject {
                put("credential_configuration_id", JsonPrimitive("identity_credential"))
            },
            requestId = "request-3",
            createdAtEpochSeconds = Clock.System.now().epochSeconds - 31,
        )

        val response = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject { put("transaction_id", JsonPrimitive(transactionId)) },
            requestId = "request-3",
        )

        assertEquals(200, response.status)
        assertEquals("deferred-issued-credential", response.payload["credentials"]?.jsonArray?.get(0)?.jsonObject?.get("credential")?.jsonPrimitive?.content)
    }

    @Test
    fun `deferred resume does not re-trigger deferred mode when issuer is globally configured as deferred`() = runTest {
        val sessionId = "session-resume"
        val service = protocolService(issuanceSession(sessionId), IssuanceMode.DEFERRED)
        val transactionId = service.registerDeferredCredentialRequest(
            sessionId = sessionId,
            intervalSeconds = 30L,
            requestParameters = buildJsonObject {
                put("credential_configuration_id", JsonPrimitive("identity_credential"))
            },
            requestId = "request-resume",
            createdAtEpochSeconds = Clock.System.now().epochSeconds - 31,
        )

        val response = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject { put("transaction_id", JsonPrimitive(transactionId)) },
            requestId = "request-resume",
        )

        assertEquals(200, response.status)
        assertEquals("deferred-issued-credential", response.payload["credentials"]?.jsonArray?.get(0)?.jsonObject?.get("credential")?.jsonPrimitive?.content)
    }

    @Test
    fun `deferred credential request rejects a missing transaction id`() = runTest {
        val sessionId = "session-456"
        val service = protocolService(issuanceSession(sessionId))

        val response = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject {},
            requestId = "request-2",
        )

        assertEquals(400, response.status)
        assertEquals(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, response.payload["error"]?.jsonPrimitive?.content)
        assertEquals("Deferred credential request requires transaction_id", response.payload["error_description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `deferred credential request is removed after use and a repeated call returns invalid transaction id`() = runTest {
        val sessionId = "session-consumed"
        val service = protocolService(issuanceSession(sessionId))
        val transactionId = service.registerDeferredCredentialRequest(
            sessionId = sessionId,
            intervalSeconds = 30L,
            requestParameters = buildJsonObject {
                put("credential_configuration_id", JsonPrimitive("identity_credential"))
            },
            requestId = "request-consumed",
            createdAtEpochSeconds = Clock.System.now().epochSeconds - 31,
        )

        val firstResponse = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject { put("transaction_id", JsonPrimitive(transactionId)) },
            requestId = "request-consumed",
        )
        assertEquals(200, firstResponse.status)

        val secondResponse = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject { put("transaction_id", JsonPrimitive(transactionId)) },
            requestId = "request-consumed-again",
        )

        assertEquals(400, secondResponse.status)
        assertEquals(CredentialErrorCodes.INVALID_TRANSACTION_ID, secondResponse.payload["error"]?.jsonPrimitive?.content)
        assertEquals("Deferred credential request contains an invalid transaction_id", secondResponse.payload["error_description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `deferred credential request rejects an invalid transaction id`() = runTest {
        val sessionId = "session-789"
        val service = protocolService(issuanceSession(sessionId))

        val response = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject { put("transaction_id", JsonPrimitive("missing-transaction")) },
            requestId = "request-4",
        )

        assertEquals(400, response.status)
        assertEquals(CredentialErrorCodes.INVALID_TRANSACTION_ID, response.payload["error"]?.jsonPrimitive?.content)
        assertEquals("Deferred credential request contains an invalid transaction_id", response.payload["error_description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `deferred credential request rejects when the issuance session is no longer active`() = runTest {
        val sessionId = "session-closed"
        val service = protocolService(issuanceSession(sessionId).copy(status = IssuanceSessionStatus.UNSUCCESSFUL, isClosed = true))
        val transactionId = service.registerDeferredCredentialRequest(sessionId = sessionId, intervalSeconds = 30L)

        val response = service.processDeferredCredentialRequest(
            authorizationHeaders = listOf(jwtWithSub(sessionId)),
            dpopProofHeaderValues = emptyList(),
            parameters = buildJsonObject { put("transaction_id", JsonPrimitive(transactionId)) },
            requestId = "request-closed",
        )

        assertEquals(400, response.status)
        assertEquals(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, response.payload["error"]?.jsonPrimitive?.content)
    }

    private fun protocolService(
        session: IssuanceSession,
        issuanceMode: IssuanceMode = IssuanceMode.SYNC,
    ): OpenId4VciProtocolService {
        val serviceConfig = Issuer2ServiceConfig(
            baseUrl = "http://localhost",
            credentialIssuanceMode = issuanceMode,
        )
        val metadataConfig = Issuer2MetadataConfig(
            credentialConfigurations = mapOf(
                "identity_credential" to buildJsonObject {
                    put("format", JsonPrimitive("dc+sd-jwt"))
                    put("vct", JsonPrimitive("identity_credential"))
                    put("scope", JsonPrimitive("identity_credential"))
                }
            )
        )
        val profileService = CredentialProfileService(
            profilesConfig = Issuer2ProfilesConfig(),
            metadataConfig = metadataConfig,
        )
        val sessionService = IssuanceSessionService(TestSessionRepository(session))
        val metadataService = MetadataService(
            serviceConfig = serviceConfig,
            metadataConfig = metadataConfig,
            profileService = profileService,
            sessionService = sessionService,
            preAuthorizedGrantAnonymousAccessSupported = true,
        )

        return OpenId4VciProtocolService(
            oauth2Provider = stubOAuth2Provider(),
            sessionService = sessionService,
            profileService = profileService,
            metadataService = metadataService,
            notificationService = IssuanceNotificationService(),
            credentialNonceService = testNonceService(),
            defaultCredentialIssuanceMode = issuanceMode,
        )
    }

    private suspend fun issuanceSession(sessionId: String): IssuanceSession {
        val session = IssuanceSession(
            sessionId = sessionId,
            profileId = "profile",
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            credentialConfigurationId = "identity_credential",
            issuerKey = buildJsonObject {
                put("type", JsonPrimitive("jwk"))
                put(
                    "jwk",
                    Json.parseToJsonElement(
                        """{"kty":"OKP","crv":"Ed25519","d":"LjxmEnd5oC7hFabwjKQFyeIgMG0OVZ_EBZQ0ZTKBZQs","x":"UDiPRbt76NoaAye5AonMirL7jjTKppMSzAXH0ZwuenU"}""",
                    ),
                )
            },
            credentialData = buildJsonObject {},
            expiresAt = Clock.System.now() + 5.minutes,
            status = IssuanceSessionStatus.ACTIVE,
        )
        return IssuanceSessionCrypto2Keys.attachFromIssuerKey(session)
    }

    private fun jwtWithSub(sessionId: String): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$sessionId"}""".toByteArray())
        return "Be" + "arer" + " " + header + "." + payload + "."
    }

    private fun stubOAuth2Provider(): OAuth2Provider = object : OAuth2Provider {
        override suspend fun createAuthorizationRequest(parameters: Map<String, List<String>>): AuthorizationRequestResult = error("Not used")
        override suspend fun createAuthorizationResponse(authorizationRequest: AuthorizationRequest, session: Session): AuthorizationResponseResult = error("Not used")
        override fun writeAuthorizationError(error: OAuthError): AuthorizationResponseHttp = error("Not used")
        override fun writeAuthorizationError(authorizationRequest: AuthorizationRequest, error: OAuthError): AuthorizationResponseHttp = error("Not used")
        override fun writeAuthorizationResponse(authorizationRequest: AuthorizationRequest, response: AuthorizationResponse): AuthorizationResponseHttp = error("Not used")
        override suspend fun createPushedAuthorizationRequest(parameters: Map<String, List<String>>, headers: Map<String, List<String>>): AuthorizationRequestResult = error("Not used")
        override suspend fun createPushedAuthorizationResponse(authorizationRequest: AuthorizationRequest, clientAuthentication: Map<String, String>): PushedAuthorizationResponseResult = error("Not used")
        override fun writePushedAuthorizationError(error: OAuthError): PushedAuthorizationResponseHttp = error("Not used")
        override fun writePushedAuthorizationError(authorizationRequest: AuthorizationRequest, error: OAuthError): PushedAuthorizationResponseHttp = error("Not used")
        override fun writePushedAuthorizationResponse(authorizationRequest: AuthorizationRequest, response: PushedAuthorizationResponse): PushedAuthorizationResponseHttp = error("Not used")
        override suspend fun createAccessTokenRequest(parameters: Map<String, List<String>>, headers: Map<String, List<String>>, session: Session?, tokenEndpointUri: String?): AccessTokenRequestResult = error("Not used")
        override suspend fun createAccessTokenResponse(request: AccessTokenRequest, options: TokenResponseOptions): AccessTokenResponseResult = error("Not used")
        override fun writeAccessTokenError(error: OAuthError): AccessTokenResponseHttp = error("Not used")
        override fun writeAccessTokenError(request: AccessTokenRequest, error: OAuthError): AccessTokenResponseHttp = error("Not used")
        override fun writeAccessTokenResponse(request: AccessTokenRequest, response: AccessTokenResponse): AccessTokenResponseHttp = error("Not used")
        override suspend fun createCredentialRequest(parameters: Map<String, List<String>>, session: Session?, accessTokenContext: CredentialAccessTokenContext?): CredentialRequestResult {
            val credentialConfigurationId = parameters["credential_configuration_id"]?.firstOrNull()
            val credentialIdentifier = parameters["credential_identifier"]?.firstOrNull()
            return CredentialRequestResult.Success(
                DefaultCredentialRequest(
                    client = DefaultClient("client", emptyList(), emptySet(), emptySet()),
                    credentialIdentifier = credentialIdentifier,
                    credentialConfigurationId = credentialConfigurationId,
                    proofs = null,
                    credentialResponseEncryption = null,
                    requestForm = parameters,
                    session = session ?: DefaultSession(subject = "session-0"),
                ),
            )
        }

        override suspend fun createCredentialRequest(encryptedCredentialRequest: String, session: Session?, accessTokenContext: CredentialAccessTokenContext?): CredentialRequestResult = error("Not used")
        @Deprecated("Use the Crypto2CredentialSigningKey overload", ReplaceWith(
            "CredentialResponseResult.Success( CredentialResponse( credentials = listOf( IssuedCredential( credential = JsonPrimitive( \"deferred-issued-credential\" ) ) ) ) )",
            "id.walt.openid4vci.responses.credential.CredentialResponseResult",
            "id.walt.openid4vci.responses.credential.CredentialResponse",
            "id.walt.openid4vci.responses.credential.IssuedCredential",
            "kotlinx.serialization.json.JsonPrimitive"
        )
        )
        override suspend fun createCredentialResponse(request: CredentialRequest, configuration: CredentialConfiguration, issuerKey: Key, issuerId: String, credentialData: JsonObject, dataMapping: JsonObject?, selectiveDisclosure: SDMap?, x5Chain: List<X509Certificate>?, display: List<CredentialDisplay>?, w3cVersion: String?, mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>?, authorizedTransactionDataTypes: List<String>?, credentialStatus: Status?, validFrom: Instant?, validUntil: Instant?, proofValidationContext: CredentialProofValidationContext?): CredentialResponseResult = CredentialResponseResult.Success(
            CredentialResponse(
                credentials = listOf(
                    IssuedCredential(
                        credential = JsonPrimitive("deferred-issued-credential")
                    )
                )
            )
        )
        override suspend fun createCredentialResponse(request: CredentialRequest, configuration: CredentialConfiguration, issuerKey: Crypto2CredentialSigningKey, issuerId: String, credentialData: JsonObject, dataMapping: JsonObject?, selectiveDisclosure: SDMap?, x5Chain: List<X509Certificate>?, display: List<CredentialDisplay>?, w3cVersion: String?, mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>?, authorizedTransactionDataTypes: List<String>?, credentialStatus: Status?, validFrom: Instant?, validUntil: Instant?, proofValidationContext: CredentialProofValidationContext?): CredentialResponseResult = CredentialResponseResult.Success(
            CredentialResponse(
                credentials = listOf(
                    IssuedCredential(
                        credential = JsonPrimitive("deferred-issued-credential")
                    )
                )
            )
        )
        override fun writeCredentialError(error: CredentialError): CredentialResponseHttp = CredentialResponseHttp(
            status = 400,
            payload = mapOf(
                "error" to JsonPrimitive(error.error),
                "error_description" to JsonPrimitive(error.description ?: ""),
            ),
        )
        override fun writeCredentialError(request: CredentialRequest, error: CredentialError): CredentialResponseHttp = writeCredentialError(error)
        override fun writeCredentialError(error: OAuthError): CredentialResponseHttp = CredentialResponseHttp(
            status = 400,
            payload = mapOf(
                "error" to JsonPrimitive(error.error),
                "error_description" to JsonPrimitive(error.description ?: ""),
            ),
        )
        override fun writeCredentialError(request: CredentialRequest, error: OAuthError): CredentialResponseHttp = writeCredentialError(error)
        override suspend fun writeCredentialResponse(request: CredentialRequest, response: CredentialResponse): CredentialResponseHttp = CredentialResponseHttp(
            status = 200,
            payload = mapOf(
                "credentials" to buildJsonArray {
                    add(buildJsonObject {
                        put("credential", JsonPrimitive("deferred-issued-credential"))
                    })
                },
            ),
            headers = mapOf("Cache-Control" to "no-store"),
        )
    }

    private fun testNonceService(): CredentialNonceService = object : CredentialNonceService {
        override suspend fun issue(binding: CredentialNonceBinding): IssuedCredentialNonce = IssuedCredentialNonce("nonce")
        override suspend fun validate(nonce: String, binding: CredentialNonceBinding): CredentialNonceValidationResult = CredentialNonceValidationResult.INVALID
    }

    private class TestSessionRepository(private val session: IssuanceSession) : IssuanceSessionRepository {
        override suspend fun save(session: IssuanceSession): IssuanceSession = session
        override suspend fun get(sessionId: String): IssuanceSession? = if (sessionId == session.sessionId) session else null
        override suspend fun take(sessionId: String): IssuanceSession? = if (sessionId == session.sessionId) session else null
        override suspend fun list(): List<IssuanceSession> = listOf(session)
        override suspend fun remove(sessionId: String) = Unit
    }
}

package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.clientauth.AuthenticatedClient
import id.walt.openid4vci.clientauth.ClientAuthenticationServiceConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationMethod
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.par.PARRecord
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.validation.AuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import id.walt.openid4vci.core.PushedAuthorizationConfig
import id.walt.openid4vci.requests.authorization.*
import kotlinx.serialization.json.*

class ProviderPushedAuthorizationFlowTest {

    @Test
    fun `direct authorization completes without PAR configuration or PAR storage access`() = runTest {
        val unavailableRepository = object : PARRepository {
            override suspend fun save(record: PARRecord) = error("Direct authorization must not save PAR")

            override suspend fun consume(requestId: String, now: kotlin.time.Instant): PARRecord? =
                error("Direct authorization must not consume PAR")
        }
        for (parConfig in listOf(null, PushedAuthorizationConfig(unavailableRepository))) {
            val provider = buildOAuth2Provider(createTestConfig().copy(pushedAuthorizationConfig = parConfig))
            val parameters = validPushedParameters()
            val request = assertIs<AuthorizationRequestResult.Success>(
                provider.createAuthorizationRequest(parameters)
            ).request.withIssuer("https://issuer.example")
            val authorization = assertIs<AuthorizationResponseResult.Success>(
                provider.createAuthorizationResponse(request, DefaultSession(subject = "wallet-user"))
            ).response
            assertEquals("state-123", authorization.state)

            val tokenRequest = assertIs<AccessTokenRequestResult.Success>(
                provider.createAccessTokenRequest(mapOf(
                    "grant_type" to listOf(GrantType.AuthorizationCode.value),
                    "client_id" to parameters.getValue("client_id"),
                    "redirect_uri" to parameters.getValue("redirect_uri"),
                    "code" to listOf(authorization.code),
                ))
            ).request.withIssuer("https://issuer.example")
            val token = assertIs<AccessTokenResponseResult.Success>(
                provider.createAccessTokenResponse(tokenRequest)
            ).response
            assertTrue(token.accessToken.isNotBlank())
        }
    }

    @Test
    fun `invalid PAR references cannot fall back to direct authorization in any configuration`() = runTest {
        for (required in listOf<Boolean?>(null, false, true)) {
            val provider = buildOAuth2Provider(createTestConfig().copy(
                pushedAuthorizationConfig = required?.let {
                    PushedAuthorizationConfig(InMemoryPARRepository(), enforcePushedAuthorizationRequests = { it })
                },
            ))
            for (requestUri in listOf(
                "urn:ietf:params:oauth:request_uri:missing",
                "urn:ietf:params:oauth:request_uri:",
                "https://wallet.example/request",
            )) {
                val error = assertIs<AuthorizationRequestResult.Failure>(
                    provider.createAuthorizationRequest(
                        validPushedParameters() + ("request_uri" to listOf(requestUri))
                    )
                ).error
                assertEquals(
                    if (required == true && requestUri.startsWith("https://")) OAuthErrorCodes.INVALID_REQUEST
                    else OAuthErrorCodes.INVALID_REQUEST_URI,
                    error.error,
                )
            }
        }
    }

    @Test
    fun `PAR revalidates stored parameters and preserves the complete serialized request`() = runTest {
        val validatedParameters = mutableListOf<Map<String, List<String>>>()
        val delegate = DefaultAuthorizationRequestValidator()
        val storage = InMemoryPARRepository()
        val repository = object : PARRepository {
            override suspend fun save(record: PARRecord) {
                val encoded = Json.encodeToString(record as DefaultPARRecord)
                storage.save(Json.decodeFromString<DefaultPARRecord>(encoded))
            }

            override suspend fun consume(requestId: String, now: kotlin.time.Instant): PARRecord? =
                storage.consume(requestId, now)
        }
        val provider = buildOAuth2Provider(createTestConfig(
            authorizationRequestValidator = AuthorizationRequestValidator { parameters ->
                validatedParameters += parameters
                val parsed = assertIs<AuthorizationRequestResult.Success>(delegate.validate(parameters)).request
                    .toDefaultAuthorizationRequest().copy(
                        requestedAudience = setOf("requested-audience"), grantedAudience = setOf("granted-audience"),
                        grantedScopes = setOf("credential"), handledResponseTypes = setOf("code"),
                        issClaim = "https://issuer.example", responseMode = ResponseMode.FRAGMENT,
                        defaultResponseMode = ResponseMode.FRAGMENT,
                        authorizationDetails = listOf(AuthorizationDetail(
                            type = "openid_credential", credentialConfigurationId = "credential",
                        )),
                    )
                AuthorizationRequestResult.Success(object : AuthorizationRequest by parsed {})
            },
        ).copy(
            pushedAuthorizationConfig = PushedAuthorizationConfig(repository, enforcePushedAuthorizationRequests = { true }),
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methods = listOf(AcceptingClientSecretPostAuthenticationMethod),
                methodsByEndpoint = mapOf(ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                    setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST)),
            ),
        ))
        val parameters = validPushedParameters("auth-param-client") + mapOf(
            "code_challenge" to listOf("a".repeat(43)), "code_challenge_method" to listOf("S256"),
            "issuer_state" to listOf("offer-state"), "client_secret" to listOf("secret-value"),
        )
        val pushed = assertIs<AuthorizationRequestResult.Success>(provider.createPushedAuthorizationRequest(parameters)).request
        val response = assertIs<PushedAuthorizationResponseResult.Success>(provider.createPushedAuthorizationResponse(pushed)).response
        val restored = assertIs<AuthorizationRequestResult.Success>(provider.createAuthorizationRequest(mapOf(
            "client_id" to listOf("auth-param-client"), "request_uri" to listOf(response.requestUri),
            "redirect_uri" to listOf("https://attacker.example"), "scope" to listOf("tampered"),
            "state" to listOf("tampered"), "code_challenge" to listOf("tampered"),
        ))).request

        assertEquals(pushed.toDefaultAuthorizationRequest().copy(requestForm = parameters - "client_secret"), restored)
        assertEquals(listOf(parameters, parameters - "client_secret"), validatedParameters)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(mapOf(
                "client_id" to listOf("auth-param-client"), "request_uri" to listOf(response.requestUri),
            ))
        ).error.error)
        assertEquals(2, validatedParameters.size)
    }

    @Test
    fun `PAR redemption rejects a request disallowed by changed authorization policy`() = runTest {
        for (required in listOf(false, true)) {
            var clientAllowed = true
            val delegate = DefaultAuthorizationRequestValidator()
            val policyError = OAuthError(OAuthErrorCodes.UNAUTHORIZED_CLIENT, "Client is no longer allowed")
            val provider = buildOAuth2Provider(createTestConfig(
                authorizationRequestValidator = AuthorizationRequestValidator { parameters ->
                    if (clientAllowed) delegate.validate(parameters)
                    else AuthorizationRequestResult.Failure(policyError)
                },
            ).copy(
                pushedAuthorizationConfig = PushedAuthorizationConfig(
                    InMemoryPARRepository(), enforcePushedAuthorizationRequests = { required },
                ),
            ))
            val pushedResponse = pushAuthorizationRequest(provider, validPushedParameters())
            val authorizationParameters = mapOf(
                "client_id" to listOf("demo-client"), "request_uri" to listOf(pushedResponse.requestUri),
            )

            clientAllowed = false
            assertEquals(policyError, assertIs<AuthorizationRequestResult.Failure>(
                provider.createAuthorizationRequest(authorizationParameters)
            ).error)

            clientAllowed = true
            assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, assertIs<AuthorizationRequestResult.Failure>(
                provider.createAuthorizationRequest(authorizationParameters)
            ).error.error)
        }
    }

    @Test
    fun `PAR redemption rechecks issuer state with the configured validator`() = runTest {
        var issuerStateActive = true
        val stateError = OAuthError(OAuthErrorCodes.INVALID_REQUEST, "issuer_state is no longer active")
        val provider = buildOAuth2Provider(createTestConfig(
            issuerStateValidator = { issuerState, request ->
                assertEquals("offer-state", issuerState)
                assertEquals("demo-client", request.client.id)
                if (issuerStateActive) null else stateError
            },
        ).copy(pushedAuthorizationConfig = PushedAuthorizationConfig(InMemoryPARRepository())))
        val pushedResponse = pushAuthorizationRequest(
            provider, validPushedParameters() + ("issuer_state" to listOf("offer-state")),
        )

        issuerStateActive = false
        assertEquals(stateError, assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(mapOf(
                "client_id" to listOf("demo-client"), "request_uri" to listOf(pushedResponse.requestUri),
                "issuer_state" to listOf("tampered-state"),
            ))
        ).error)
    }

    @Test
    fun `provider stores PAR and resolves request_uri through authorization endpoint`() = runTest {
        val provider = buildParProvider()
        val pushedParameters = mapOf(
            "response_type" to listOf(ResponseType.CODE.value),
            "client_id" to listOf("demo-client"),
            "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            "scope" to listOf("openid credential"),
            "state" to listOf("state-123"),
        )

        val pushedRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createPushedAuthorizationRequest(pushedParameters)
        ).request
        val pushedResponse = assertIs<PushedAuthorizationResponseResult.Success>(
            provider.createPushedAuthorizationResponse(pushedRequest)
        ).response

        assertTrue(pushedResponse.requestUri.startsWith("urn:ietf:params:oauth:request_uri:"))
        assertEquals(90, pushedResponse.expiresIn)

        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(pushedParameters, authorizeRequest.requestForm)

        val replay = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        )
        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, replay.error.error)
    }

    @Test
    fun `provider rejects request_uri at PAR endpoint`() = runTest {
        val provider = buildParProvider()

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createPushedAuthorizationRequest(
                mapOf(
                    "response_type" to listOf(ResponseType.CODE.value),
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf("urn:ietf:params:oauth:request_uri:nested"),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST, result.error.error)
    }

    @Test
    fun `provider rejects authorize request when PAR client_id does not match`() = runTest {
        val provider = buildParProvider()
        val pushedResponse = pushAuthorizationRequest(
            provider = provider,
            parameters = validPushedParameters(clientId = "original-client"),
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("different-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST, result.error.error)
    }

    @Test
    fun `provider rejects invalid request_uri`() = runTest {
        val provider = buildParProvider()

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf("https://client.example/request/123"),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, result.error.error)
    }

    @Test
    fun `provider rejects request_uri when PAR is not configured`() = runTest {
        val provider = buildOAuth2Provider(createTestConfig())

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf("urn:ietf:params:oauth:request_uri:missing-config"),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, result.error.error)
    }

    @Test
    fun `provider returns server error when PAR endpoint is used without PAR configuration`() = runTest {
        val provider = buildOAuth2Provider(createTestConfig())
        val pushedRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createPushedAuthorizationRequest(validPushedParameters())
        ).request

        val result = assertIs<PushedAuthorizationResponseResult.Failure>(
            provider.createPushedAuthorizationResponse(pushedRequest)
        )

        assertEquals(OAuthErrorCodes.SERVER_ERROR, result.error.error)
        assertEquals("Pushed authorization requests are not configured", result.error.description)
    }

    @Test
    fun `provider rejects expired request_uri`() = runTest {
        val repository = InMemoryPARRepository()
        val provider = buildParProvider(repository)
        val parameters = validPushedParameters(clientId = "expired-client")
        val now = Clock.System.now()

        repository.save(
            DefaultPARRecord(
                requestId = "expired-request",
                authorizationRequest = assertIs<AuthorizationRequestResult.Success>(
                    provider.createPushedAuthorizationRequest(parameters)
                ).request.toDefaultAuthorizationRequest(),
                createdAt = now - 2.seconds,
                expiresAt = now - 1.seconds,
            )
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("expired-client"),
                    "request_uri" to listOf("urn:ietf:params:oauth:request_uri:expired-request"),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, result.error.error)
    }

    @Test
    fun `provider preserves pushed authorization parameters`() = runTest {
        val provider = buildParProvider()
        val pushedParameters = validPushedParameters(clientId = "multi-client") +
            ("scope" to listOf("openid credential"))

        val pushedResponse = pushAuthorizationRequest(provider, pushedParameters)
        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("multi-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(listOf("openid credential"), authorizeRequest.requestForm["scope"])
    }

    @Test
    fun `provider enforces pushed authorization requests when configured`() = runTest {
        val provider = buildParProvider(enforcePushedAuthorizationRequests = true)
        val parameters = validPushedParameters(clientId = "required-par-client")

        val directAuthorize = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(parameters)
        )
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, directAuthorize.error.error)

        val pushedResponse = pushAuthorizationRequest(provider, parameters)
        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("required-par-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(parameters, authorizeRequest.requestForm)
    }

    @Test
    fun `provider strips endpoint-only client authentication parameters before storing PAR`() = runTest {
        val provider = buildParProvider(
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methods = listOf(AcceptingClientSecretPostAuthenticationMethod),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                        setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST),
                ),
            ),
        )
        val pushedParameters = validPushedParameters(clientId = "auth-param-client") +
            mapOf(
                "client_secret" to listOf("secret-value"),
            )

        val pushedResponse = pushAuthorizationRequest(provider, pushedParameters)
        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("auth-param-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(null, authorizeRequest.requestForm["client_secret"])
        assertEquals("auth-param-client", authorizeRequest.requestForm["client_id"]?.singleOrNull())
    }

    @Test
    fun `provider ignores client authentication parameters when PAR client authentication is disabled`() = runTest {
        val provider = buildParProvider()

        val result = assertIs<AuthorizationRequestResult.Success>(
            provider.createPushedAuthorizationRequest(
                validPushedParameters(clientId = "ignored-auth-client") +
                    mapOf(
                        "client_secret" to listOf("secret-value"),
                    )
            )
        )

        assertEquals(null, result.request.authenticatedClient)
    }

    @Test
    fun `provider rejects non-configured client authentication method at PAR endpoint`() = runTest {
        val provider = buildParProvider(
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                        setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
                ),
            ),
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createPushedAuthorizationRequest(
                validPushedParameters(clientId = "unsupported-auth-client") +
                    mapOf(
                        "client_secret" to listOf("secret-value"),
                    )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_CLIENT, result.error.error)
        assertEquals(
            "Client authentication method 'client_secret_post' is not allowed for this endpoint",
            result.error.description,
        )
    }

    @Test
    fun `provider rejects unauthenticated PAR when endpoint client authentication methods are configured`() = runTest {
        val provider = buildParProvider(
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methods = listOf(AcceptingClientSecretPostAuthenticationMethod),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                        setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST),
                ),
            ),
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createPushedAuthorizationRequest(validPushedParameters(clientId = "auth-param-client"))
        )

        assertEquals(OAuthErrorCodes.INVALID_CLIENT, result.error.error)
        assertEquals("Client authentication is required for this endpoint", result.error.description)
    }

    @Test
    fun `provider authenticates PAR client before rejecting request_uri`() = runTest {
        val provider = buildParProvider(
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methods = listOf(AcceptingClientSecretPostAuthenticationMethod),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                        setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST),
                ),
            ),
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createPushedAuthorizationRequest(
                validPushedParameters() +
                    ("request_uri" to listOf("urn:ietf:params:oauth:request_uri:nested"))
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_CLIENT, result.error.error)
        assertEquals("Client authentication is required for this endpoint", result.error.description)
    }

    @Test
    fun `provider writes PAR responses with no-store headers`() = runTest {
        val provider = buildParProvider()
        val pushedRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createPushedAuthorizationRequest(validPushedParameters())
        ).request
        val pushedResponse = assertIs<PushedAuthorizationResponseResult.Success>(
            provider.createPushedAuthorizationResponse(pushedRequest)
        ).response

        val httpResponse = provider.writePushedAuthorizationResponse(pushedRequest, pushedResponse)

        assertEquals("no-store", httpResponse.headers["Cache-Control"])
        assertEquals("no-cache", httpResponse.headers["Pragma"])
    }

    @Test
    fun `dynamic policy is evaluated on each authorization and failures fail closed`() = runTest {
        var required = false
        var broken = false
        val policy = PushedAuthorizationConfig(
            repository = InMemoryPARRepository(),
            enforcePushedAuthorizationRequests = {
                check(!broken) { "Configuration unavailable" }
                required
            },
        )
        val provider = buildOAuth2Provider(createTestConfig().copy(pushedAuthorizationConfig = policy))
        assertIs<AuthorizationRequestResult.Success>(provider.createAuthorizationRequest(validPushedParameters()))
        val pushedResponse = pushAuthorizationRequest(provider, validPushedParameters())
        required = true
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(validPushedParameters())
        ).error.error)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(mapOf("client_id" to listOf("demo-client"), "request_uri" to listOf("https://example/request")))
        ).error.error)
        assertIs<AuthorizationRequestResult.Success>(provider.createAuthorizationRequest(mapOf(
            "client_id" to listOf("demo-client"), "request_uri" to listOf(pushedResponse.requestUri),
        )))
        required = false
        assertIs<AuthorizationRequestResult.Success>(provider.createAuthorizationRequest(validPushedParameters()))
        broken = true
        assertFailsWith<IllegalStateException> { provider.createAuthorizationRequest(validPushedParameters()) }
    }

    @Test
    fun `duplicate PAR references including blank values cannot bypass checks`() = runTest {
        val provider = buildParProvider(enforcePushedAuthorizationRequests = true)
        val uri = pushAuthorizationRequest(provider, validPushedParameters()).requestUri
        for (parameters in listOf(
            mapOf("client_id" to listOf("demo-client"), "request_uri" to listOf(uri, "")),
            mapOf("client_id" to listOf("demo-client", ""), "request_uri" to listOf(uri)),
        )) {
            assertEquals(OAuthErrorCodes.INVALID_REQUEST, assertIs<AuthorizationRequestResult.Failure>(
                provider.createAuthorizationRequest(parameters)
            ).error.error)
        }
        assertIs<AuthorizationRequestResult.Success>(provider.createAuthorizationRequest(
            mapOf("client_id" to listOf("demo-client"), "request_uri" to listOf(uri))
        ))
    }

    @Test
    fun `concrete snapshot serializes every declared request field`() = runTest {
        val parameters = validPushedParameters() + mapOf(
            "code_challenge" to listOf("a".repeat(43)), "code_challenge_method" to listOf("S256"),
            "issuer_state" to listOf("offer"),
        )
        val parsed = assertIs<AuthorizationRequestResult.Success>(buildParProvider().createAuthorizationRequest(parameters)).request
        val complete = parsed.toDefaultAuthorizationRequest().copy(
            handledResponseTypes = setOf("code"), grantedScopes = setOf("openid"),
            requestedAudience = setOf("requested"), grantedAudience = setOf("granted"),
            issClaim = "https://issuer.example", responseMode = ResponseMode.FRAGMENT,
            defaultResponseMode = ResponseMode.FRAGMENT,
            authorizationDetails = listOf(AuthorizationDetail(type = "openid_credential", credentialConfigurationId = "credential")),
            authenticatedClient = AuthenticatedClient("demo-client", "test", true,
                claims = buildJsonObject { put("claim", "value") }),
        )
        val custom = object : AuthorizationRequest by complete {}
        val snapshot = custom.toDefaultAuthorizationRequest()
        assertEquals(complete, snapshot)
        assertEquals(complete, Json.decodeFromString<DefaultAuthorizationRequest>(Json.encodeToString(snapshot)))
    }

    private fun buildParProvider(
        repository: InMemoryPARRepository = InMemoryPARRepository(),
        enforcePushedAuthorizationRequests: Boolean = false,
        clientAuthenticationServiceConfig: ClientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(),
    ) =
        buildOAuth2Provider(
            createTestConfig().copy(
                pushedAuthorizationConfig = PushedAuthorizationConfig(
                    repository = repository,
                    enforcePushedAuthorizationRequests = { enforcePushedAuthorizationRequests },
                ),
                clientAuthenticationServiceConfig = clientAuthenticationServiceConfig,
            )
        )

    private fun validPushedParameters(clientId: String = "demo-client"): Map<String, List<String>> =
        mapOf(
            "response_type" to listOf(ResponseType.CODE.value),
            "client_id" to listOf(clientId),
            "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            "scope" to listOf("openid credential"),
            "state" to listOf("state-123"),
        )

    private object AcceptingClientSecretPostAuthenticationMethod : ClientAuthenticationMethod {
        override val name: String = ClientAuthenticationMethods.CLIENT_SECRET_POST

        @Suppress("UNUSED_PARAMETER")
        override suspend fun authenticate(
            endpoint: ClientAuthenticationEndpoint,
            parameters: Map<String, List<String>>,
            headers: Map<String, List<String>>,
            context: ClientAuthenticationContext,
        ): ClientAuthenticationResult =
            ClientAuthenticationResult.Authenticated(
                AuthenticatedClient(
                    id = "auth-param-client",
                    authenticationMethod = name,
                )
            )
    }

    private suspend fun pushAuthorizationRequest(
        provider: OAuth2Provider,
        parameters: Map<String, List<String>>,
    ) = assertIs<PushedAuthorizationResponseResult.Success>(
        provider.createPushedAuthorizationResponse(
            assertIs<AuthorizationRequestResult.Success>(
                provider.createPushedAuthorizationRequest(parameters)
            ).request
        )
    ).response
}

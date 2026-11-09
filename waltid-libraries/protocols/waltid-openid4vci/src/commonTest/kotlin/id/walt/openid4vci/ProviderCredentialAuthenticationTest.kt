package id.walt.openid4vci

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.attestation.VerifiedAttestationLifetime
import id.walt.openid4vci.clientauth.*
import id.walt.openid4vci.clientauth.attestation.*
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.deferred.DeferredAttestationLifetimeValidator
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.AcceptedCredentialRequest
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.credential.encryption.CredentialRequestDecryptor
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.access.*
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock

class ProviderCredentialAuthenticationTest {
    @Test fun verifiedAuthenticationSurvivesIssuerContextAndIgnoresWalletSuppliedEvidence() = runTest {
        val wia = evidence()
        val claims = claimsFor(AuthenticatedClient(CLIENT, ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH, attestation = wia))
        val parameters = mapOf("credential_configuration_id" to listOf("identity"),
            "client_id" to listOf("untrusted-wallet"), "access_token_client_id" to listOf("untrusted-wallet")) +
            claims.filterKeys { it != JwtPayloadClaims.CLIENT_ID }.mapValues { listOf("untrusted-evidence") }
        for (encrypted in listOf(false, true)) {
            val accepted = accept(provider(claims, parameters = parameters), parameters, encrypted)
            assertEquals(wia, accepted.clientAttestation)
            assertEquals(true, accepted.clientAttestationRequired)
            assertEquals(CLIENT, accepted.request.accessTokenClientId)
            assertEquals("offer", accepted.request.session?.subject)
            assertEquals(ISSUER, accepted.request.issClaim)
            val restored = Json.decodeFromString<AcceptedCredentialRequest>(Json.encodeToString(accepted))
            assertEquals(accepted, restored)
        }
    }

    @Test fun legacyAuthenticationRequirementsComeFromTheExistingStaticOrDynamicConfiguration() = runTest {
        val claims = buildJsonObject { put(JwtPayloadClaims.CLIENT_ID, CLIENT) }
        for (dynamic in listOf(false, true)) for (required in listOf(false, true)) {
            val methods = if (required) setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH)
                else setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH, ClientAuthenticationMethods.PRIVATE_KEY_JWT)
            if (required) {
                val error = assertFailsWith<CredentialProofValidationException> { accept(provider(claims, methods, dynamic)) }
                assertEquals(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, error.errorCode)
            } else {
                val accepted = accept(provider(claims, methods, dynamic))
                assertNull(accepted.clientAttestation)
                assertEquals(false, accepted.clientAttestationRequired)
                assertTrue(DeferredAttestationLifetimeValidator().isValid(accepted, Clock.System.now()))
            }
        }
    }

    @Test fun originalAttestationAuthenticationRequiresEvidenceEvenWhenConfigurationChanges() = runTest {
        val claims = claimsFor(AuthenticatedClient(CLIENT, ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH))
        val error = assertFailsWith<CredentialProofValidationException> { accept(provider(claims)) }
        assertEquals(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, error.errorCode)
    }

    @Test fun expiredOriginalAttestationIsRejectedBeforeAcceptance() = runTest {
        val now = Clock.System.now().epochSeconds
        val expired = evidence().let { it.copy(lifetime = it.lifetime.copy(issuedAt = now - 300, expiresAt = now - 120)) }
        val claims = claimsFor(AuthenticatedClient(CLIENT, ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH, attestation = expired))
        for (encrypted in listOf(false, true)) {
            val error = assertFailsWith<CredentialProofValidationException> { accept(provider(claims), encrypted = encrypted) }
            assertEquals(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, error.errorCode)
        }
    }

    @Test fun malformedVerifiedAuthenticationClaimsReturnATypedTokenError() = runTest {
        val valid = claimsFor(AuthenticatedClient(CLIENT, ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH, attestation = evidence()))
        val malformed = JsonObject(valid.mapValues { (name, value) ->
            if (name == JwtPayloadClaims.CLIENT_ID) value else JsonPrimitive("invalid-private-claim")
        })
        val result = assertIs<CredentialRequestResult.OAuthFailure>(provider(malformed).createCredentialRequest(
            PARAMETERS, accessTokenContext = TOKEN_CONTEXT,
        ))
        assertEquals("invalid_token", result.error.error)
    }

    @Test fun bothGrantsPreserveOriginalAuthenticationThroughRefreshIncludingAnonymousAccess() = runTest {
        for ((authorizationCode, anonymous) in listOf(true to false, false to false, false to true)) {
            val wia = evidence()
            val originalClient = if (anonymous) null else AuthenticatedClient(CLIENT, ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH, attestation = wia)
            val signedClaims = mutableListOf<Map<String, Any?>>()
            val config = createTestConfig(accessTokenIssuer = object : AccessTokenIssuer {
                override suspend fun issue(claims: Map<String, Any?>): String {
                    signedClaims += claims
                    return "issued-${signedClaims.size}"
                }
            })
            val tokenProvider = buildOAuth2Provider(config)
            val tokenParameters = if (authorizationCode) {
                val authorization = assertIs<AuthorizationRequestResult.Success>(tokenProvider.createAuthorizationRequest(mapOf(
                    "response_type" to listOf("code"), "client_id" to listOf(CLIENT),
                    "redirect_uri" to listOf(CALLBACK), "scope" to listOf("openid"),
                )))
                val response = assertIs<AuthorizationResponseResult.Success>(tokenProvider.createAuthorizationResponse(
                    authorization.request.withIssuer(ISSUER), DefaultSession(subject = "offer"),
                ))
                mapOf("grant_type" to listOf(GrantType.AuthorizationCode.value), "client_id" to listOf(CLIENT),
                    "code" to listOf(response.response.code), "redirect_uri" to listOf(CALLBACK))
            } else {
                val issued = config.preAuthorizedCodeIssuer.issue(PreAuthorizedCodeIssueRequest(
                    clientId = if (anonymous) null else CLIENT, scopes = setOf("openid"), session = DefaultSession(subject = "offer"),
                ))
                mapOf("grant_type" to listOf(GrantType.PreAuthorizedCode.value), "pre-authorized_code" to listOf(issued.code)) +
                    if (anonymous) emptyMap() else mapOf("client_id" to listOf(CLIENT))
            }
            val initialRequest = assertIs<AccessTokenRequestResult.Success>(tokenProvider.createAccessTokenRequest(tokenParameters))
            val initial = assertIs<AccessTokenResponseResult.Success>(tokenProvider.createAccessTokenResponse(
                initialRequest.request.withIssuer(ISSUER).withAuthenticatedClient(originalClient),
            ))
            val initialClaims = credentialClaims(signedClaims.last())
            val refreshParameters = mapOf("grant_type" to listOf(GrantType.RefreshToken.value),
                "refresh_token" to listOf(assertNotNull(initial.response.refreshToken))) +
                if (anonymous) emptyMap() else mapOf("client_id" to listOf(CLIENT))
            val refreshRequest = assertIs<AccessTokenRequestResult.Success>(tokenProvider.createAccessTokenRequest(refreshParameters))
            val freshClient = originalClient?.copy(attestation = evidence())
            assertIs<AccessTokenResponseResult.Success>(tokenProvider.createAccessTokenResponse(
                refreshRequest.request.withIssuer(ISSUER).withAuthenticatedClient(freshClient),
            ))
            val refreshedClaims = credentialClaims(signedClaims.last())
            assertEquals(initialClaims.verifiedClientAuthentication(), refreshedClaims.verifiedClientAuthentication())
            assertEquals(if (anonymous) null else wia, refreshedClaims.verifiedClientAttestation())
            assertFalse(JwtPayloadClaims.PRE_AUTHORIZED_CODE in refreshedClaims)
            val accepted = accept(provider(refreshedClaims, setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH)))
            assertEquals(anonymous, accepted.request.anonymousPreAuthorizedAccess)
            assertEquals(!anonymous, accepted.clientAttestationRequired)
            assertEquals(if (anonymous) null else wia, accepted.clientAttestation)
        }
    }

    private fun provider(
        claims: JsonObject,
        methods: Set<String> = emptySet(),
        dynamic: Boolean = false,
        parameters: Map<String, List<String>> = PARAMETERS,
    ): OAuth2Provider {
        val authentication = ClientAuthenticationServiceConfig(
            methods = methods.map { method -> object : ClientAuthenticationMethod {
                override val name = method
                override suspend fun authenticate(endpoint: ClientAuthenticationEndpoint, parameters: Map<String, List<String>>,
                    headers: Map<String, List<String>>, context: ClientAuthenticationContext): ClientAuthenticationResult =
                    error("Credential processing must not replay client authentication")
            } },
            methodsByEndpoint = if (methods.isEmpty()) emptyMap() else mapOf(ClientAuthenticationEndpoint.TOKEN to methods),
        )
        val resolver = if (!dynamic) null else ClientAuthenticationServiceResolver { endpoint, requestParameters, headers ->
            assertEquals(ClientAuthenticationEndpoint.TOKEN, endpoint)
            assertEquals(listOf(CLIENT), requestParameters["client_id"])
            assertTrue(headers.isEmpty())
            ClientAuthenticationServiceResolution(authentication)
        }
        val config = createTestConfig(accessTokenVerifier = object : AccessTokenVerifier {
            override suspend fun verify(token: String, expectedIssuer: String?, expectedAudience: String?): JsonObject {
                assertEquals("verified-token", token)
                return claims
            }
        }, credentialRequestDecryptor = CredentialRequestDecryptor {
            JsonObject(parameters.mapValues { JsonPrimitive(it.value.single()) })
        }).copy(clientAuthenticationServiceConfig = authentication, clientAuthenticationServiceResolver = resolver)
        config.credentialEndpointHandlers.register(CONFIGURATION.format, CredentialEndpointHandler { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
            error("Acceptance must not sign credentials")
        })
        return buildOAuth2Provider(config)
    }

    private suspend fun accept(
        provider: OAuth2Provider,
        parameters: Map<String, List<String>> = PARAMETERS,
        encrypted: Boolean = false,
    ): AcceptedCredentialRequest {
        val result = if (encrypted) provider.createCredentialRequest("encrypted-request", accessTokenContext = TOKEN_CONTEXT)
            else provider.createCredentialRequest(parameters, accessTokenContext = TOKEN_CONTEXT)
        val request = assertIs<CredentialRequestResult.Success>(result).request
            .withSession(DefaultSession(subject = "offer")).withIssuer(ISSUER)
        return provider.acceptCredentialRequest(request, CONFIGURATION, "credential", "identity", CredentialProofValidationContext(ISSUER))
    }

    private suspend fun evidence(): VerifiedClientAttestation {
        val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("wallet-instance"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val now = Clock.System.now().epochSeconds
        return VerifiedClientAttestation(CLIENT, key.exportPublicJwkObject(), VerifiedAttestationLifetime(now - 10, now + 300, clockSkewSeconds = 60))
    }

    private fun claimsFor(client: AuthenticatedClient) = JsonObject(
        DefaultSession().withClientAuthentication(client).clientAuthenticationClaims() + (JwtPayloadClaims.CLIENT_ID to JsonPrimitive(client.id)),
    )

    private fun credentialClaims(claims: Map<String, Any?>): JsonObject = JsonObject(buildMap {
        claims.forEach { (name, value) -> if (value is JsonElement) put(name, value) }
        (claims[JwtPayloadClaims.CLIENT_ID] as? String)?.let { put(JwtPayloadClaims.CLIENT_ID, JsonPrimitive(it)) }
        (claims[JwtPayloadClaims.PRE_AUTHORIZED_CODE] as? String)?.let { put(JwtPayloadClaims.PRE_AUTHORIZED_CODE, JsonPrimitive(it)) }
    })

    companion object {
        private const val ISSUER = "https://issuer.example"
        private const val CLIENT = "demo-client"
        private const val CALLBACK = "https://openid4vci.walt.id/callback"
        private val PARAMETERS = mapOf("credential_configuration_id" to listOf("identity"))
        private val CONFIGURATION = CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity")
        private val TOKEN_CONTEXT = CredentialAccessTokenContext(AccessTokenAuthorization(AccessTokenAuthorizationScheme.BEARER, "verified-token"), ISSUER)
    }
}

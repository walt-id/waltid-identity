@file:Suppress("DEPRECATION")

package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.AttestationChallengeGateway
import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.LocalOAuthIdentityProvider
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.*
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.clientauth.ClientAuthenticationConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationMethodConfig
import id.walt.openid4vci.clientauth.attestation.ClientAttestationJwtTypes
import id.walt.openid4vci.clientauth.attestation.challenge.AttestationChallengeConfig
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerificationMethod
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifierConfig
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.wallet2.data.StoredCredentialMetadata
import id.walt.wallet2.handlers.*
import id.walt.wallet2.server.models.ResolveOfferDetailedResponse
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Real OSS wallet/issuer routes, with only the wallet provider replaced by a local test attester. */
class Wallet2ClientAttestationChallengeIntegrationTest {
    @Test fun `both grants with challenge endpoint`() = runFlow(responseOnly = false)
    @Test fun `both grants recover from issuer challenge errors`() = runFlow(responseOnly = true)

    private fun runFlow(responseOnly: Boolean) {
        val port = freePort()
        val baseUrl = "http://127.0.0.1:$port"
        AttestationChallengeGateway(baseUrl, responseOnly).use { gateway ->
        LocalOAuthIdentityProvider().use { identityProvider ->
        HttpClient().use { oauthClient ->
        val redirectUri = "openid://challenge-callback"
        val issuerKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }
        val attesterKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }
        val configurationId = "challenge-identity"
        val clientId = "challenge-wallet"
        val issuer = Issuer2Module(
            Issuer2ServiceConfig(
                baseUrl = gateway.baseUrl,
                enforcePushedAuthorizationRequests = true,
                clientAuthenticationConfig = ClientAuthenticationConfig(listOf(
                    ClientAuthenticationMethodConfig.ClientAttestation(
                        config = ClientAttestationVerifierConfig(ClientAttestationVerificationMethod.StaticJwk(
                            runBlocking { attesterKey.getPublicKey().exportJWKObject() },
                        )),
                        challenge = AttestationChallengeConfig(),
                    ),
                )),
            ),
            Issuer2MetadataConfig(credentialConfigurations = mapOf(configurationId to Json.encodeToJsonElement(
                CredentialConfiguration(
                    format = CredentialFormat.SD_JWT_VC, vct = "https://credentials.example/challenge-identity",
                    scope = configurationId, cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                    proofTypesSupported = mapOf("jwt" to ProofTypeMetadata(proofSigningAlgValuesSupported = setOf("ES256"))),
                ),
            ))),
            Issuer2ProfilesConfig(profiles = mapOf("identity" to CredentialProfileConfig(
                name = "Identity", credentialConfigurationId = configurationId,
                issuerKey = Json.parseToJsonElement(KeySerialization.serializeKey(issuerKey)).jsonObject,
                issuerDid = runBlocking { DidKeyRegistrar().registerByKey(issuerKey, DidKeyCreateOptions()).did },
                credentialData = buildJsonObject { put("given_name", "Alice") },
            ))),
        )
        var attesterCalls = 0
        suspend fun attest(publicJwk: JsonObject): String = attesterKey.signJws(
            buildJsonObject {
                put("sub", clientId)
                put("iat", Clock.System.now().epochSeconds)
                put("exp", Clock.System.now().epochSeconds + 300)
                putJsonObject("cnf") { put("jwk", publicJwk) }
            }.toString().encodeToByteArray(),
            mapOf("typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION)),
        )
        E2ETest("127.0.0.1", port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(
                    publicBaseUrl = Url(baseUrl),
                    attestationConfig = WalletAttestationConfig("$baseUrl/test-attester", buildJsonObject {
                        put("jwk", "{{public_jwk}}")
                    }),
                ))
            },
            init = { DidService.minimalInit() },
            module = {
                install(io.ktor.server.sse.SSE)
                wallet2Module(withPlugins = false)
                install(CallId) { generate { Uuid.random().toString() } }
                authentication {
                    oauth("auth-oauth") {
                        client = oauthClient
                        urlProvider = { "${gateway.baseUrl}/openid4vci/external/oauth/callback" }
                        providerLookup = { OAuthServerSettings.OAuth2ServerSettings(
                            name = "challenge-test-idp", authorizeUrl = "${identityProvider.baseUrl}/authorize",
                            accessTokenUrl = "${identityProvider.baseUrl}/token", requestMethod = HttpMethod.Post,
                            clientId = "local-issuer", clientSecret = "local-test-only", defaultScopes = listOf("openid"),
                        ) }
                    }
                }
                routing {
                    issuer.managementController.register(this)
                    issuer.openId4VciController.register(this)
                    post("/test-attester") {
                        attesterCalls++
                        val jwk = call.receive<JsonObject>().getValue("jwk").jsonObject
                        assertFalse("d" in jwk)
                        call.respond(buildJsonObject { put("walletInstanceAttestation", attest(jwk)) })
                    }
                }
            },
        ) {
            testAndReturn("Challenge-enabled OSS Wallet2 to issuer2") {
                testHttpClient(doFollowRedirects = false).use { http ->
                    val metadata = http.get("${gateway.baseUrl}/.well-known/oauth-authorization-server/openid4vci")
                        .body<AuthorizationServerMetadata>()
                    assertEquals(if (responseOnly) null else "${gateway.baseUrl}/openid4vci/challenge", metadata.challengeEndpoint)
                    assertTrue("attest_jwt_client_auth" in metadata.tokenEndpointAuthMethodsSupported.orEmpty())
                    val walletId = http.post("/wallet") { setBody(CreateWalletRequest()) }
                        .also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }
                        .body<WalletCreatedResponse>().walletId
                    val path = "/wallet/$walletId"
                    try {
                        http.post("$path/keys/generate") {
                            setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(KeyType.secp256r1))
                        }.also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }
                        val receivedIds = mutableSetOf<String>()
                        for (grant in listOf("PRE_AUTHORIZED", "PRE_AUTHORIZED", "AUTHORIZED", "AUTHORIZED")) {
                            val before = gateway.exchanges.size
                            suspend fun offer(authMethod: String): Url = http.post("/issuer2/credential-offers") {
                                setBody(buildJsonObject {
                                    put("profileId", "identity"); put("authMethod", authMethod); put("valueMode", "BY_VALUE")
                                })
                            }.also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }
                                .body<JsonObject>().getValue("credentialOffer").jsonPrimitive.content.let(::Url)

                            val offer = offer(grant)
                            val received = if (grant == "PRE_AUTHORIZED") {
                                http.post("$path/credentials/receive") {
                                    setBody(ReceiveCredentialRequest(offerUrl = offer, clientId = clientId))
                                }
                            } else {
                                val authorization = http.post("$path/credentials/receive/authorization-url") {
                                    setBody(GenerateAuthorizationUrlRequest(offerUrl = offer, clientId = clientId, redirectUri = Url(redirectUri)))
                                }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }.body<GenerateAuthorizationUrlResult>()
                                assertEquals(setOf("client_id", "request_uri"), authorization.authorizationUrl.parameters.names())
                                var callback = authorization.authorizationUrl
                                repeat(8) {
                                    if (!callback.toString().startsWith("$redirectUri?")) {
                                        val response = http.get(callback)
                                        assertTrue(response.status in listOf(HttpStatusCode.Found, HttpStatusCode.SeeOther), response.bodyAsText())
                                        callback = Url(assertNotNull(response.headers[HttpHeaders.Location]))
                                    }
                                }
                                assertEquals(redirectUri, callback.toString().substringBefore('?'))
                                assertEquals(authorization.state, callback.parameters["state"])
                                val resolved = http.post("$path/credentials/receive/resolve-offer") {
                                    setBody(ResolveOfferRequest(offer))
                                }.body<ResolveOfferDetailedResponse>()
                                http.post("$path/credentials/receive/authorized") {
                                    setBody(ReceiveAuthorizedCredentialRequest(
                                        code = assertNotNull(callback.parameters["code"]),
                                        codeVerifier = assertNotNull(authorization.codeVerifier),
                                        credentialIssuer = resolved.credentialIssuer,
                                        credentialEndpoint = resolved.credentialEndpoint,
                                        nonceEndpoint = resolved.nonceEndpoint,
                                        credentialConfigurationId = configurationId,
                                        clientId = clientId, redirectUri = Url(redirectUri),
                                    ))
                                }
                            }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }.body<ReceiveCredentialResult>()
                            assertEquals(1, received.credentialIds.size)
                            receivedIds.addAll(received.credentialIds)
                            assertEquals(receivedIds, http.get("$path/credentials")
                                .body<List<StoredCredentialMetadata>>().map { it.id }.toSet())
                            val exchanges = gateway.exchanges.drop(before)
                            val challengeCalls = exchanges.filter { it.path.endsWith("/challenge") }
                            assertEquals(if (responseOnly) 0 else 1, challengeCalls.size)
                            challengeCalls.forEach { assertEquals("no-store", it.cacheControl) }
                            for (endpoint in if (grant == "AUTHORIZED") listOf("par", "token") else listOf("token")) {
                                val attempts = exchanges.filter { it.path.endsWith("/$endpoint") }
                                assertEquals(if (responseOnly) 2 else 1, attempts.size, endpoint)
                                assertEquals(if (endpoint == "par") 201 else 200, attempts.last().status)
                                assertEquals(1, attempts.map { it.form }.distinct().size, "Grant and PKCE parameters must be preserved")
                                assertEquals(1, attempts.map { it.attestation }.distinct().size)
                                assertEquals(attempts.size, attempts.map { it.pop }.distinct().size)
                                if (responseOnly) {
                                    assertEquals(400, attempts.first().status)
                                    assertEquals("use_attestation_challenge", attempts.first().error)
                                    val replacement = assertNotNull(attempts.first().challenge)
                                    val payload = Json.parseToJsonElement(String(java.util.Base64.getUrlDecoder().decode(attempts.last().pop!!.split('.')[1]))).jsonObject
                                    assertEquals(replacement, payload["challenge"]?.jsonPrimitive?.content)
                                    assertEquals(metadata.issuer, payload["aud"]?.jsonPrimitive?.content)
                                }
                            }
                        }
                    } finally {
                        http.delete(path)
                    }
                }
            }
        }
        }
        }
        }
    }
}

package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.Key
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.credentials.examples.MdocsExamples
import id.walt.credentials.examples.SdJwtExamples
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class WalletIssuanceSessionServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun unrecognizedOfferParametersCannotOverrideMetadataDiscovery() = runTest {
        val requestedUrls = mutableListOf<String>()
        val service = service { request ->
            requestedUrls += request.url.toString()
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata())
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val offerJson = preAuthorizedRequest().offerJson!!.toMutableMap().apply {
            put(
                "credential_issuer_metadata",
                Json.parseToJsonElement(
                    """
                    {
                      "credential_issuer": "$ISSUER",
                      "credential_endpoint": "https://attacker.example/credential",
                      "authorization_servers": ["https://attacker.example"],
                      "credential_configurations_supported": {
                        "attacker-credential": {"format": "jwt_vc_json"}
                      }
                    }
                    """.trimIndent(),
                ),
            )
            put(
                "authorization_server_metadata",
                Json.parseToJsonElement(
                    """
                    {
                      "issuer": "https://attacker.example",
                      "authorization_endpoint": "https://attacker.example/authorize",
                      "token_endpoint": "https://attacker.example/token",
                      "response_types_supported": ["code"]
                    }
                    """.trimIndent(),
                ),
            )
        }.let(::JsonObject)

        val session = service.start(preAuthorizedRequest().copy(offerJson = offerJson))

        assertEquals("test-credential", session.offer.credentials.single().configurationId)
        assertEquals(listOf(ISSUER_METADATA, AS_METADATA), requestedUrls)
    }

    @Test
    fun clientAttestationChallengesAreFetchedReusedAndAdvancedFromResponses() = runTest {
        val challengeRequests = mutableListOf<String>()
        var parPopChallenge: String? = null
        var tokenPopChallenge: String? = null
        val sessionStore = RecordingSessionStore()
        val service = service(
            handler = { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(attestedAuthorizationServerMetadata())
                    CHALLENGE_ENDPOINT -> {
                        assertEquals("POST", request.method.value)
                        assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.Accept])
                        challengeRequests += request.url.toString()
                        jsonResponse("""{"attestation_challenge":"challenge-1"}""")
                    }
                    PAR_ENDPOINT -> {
                        parPopChallenge = jwtPart(
                            requireNotNull(request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP]),
                            1,
                        )["challenge"]?.jsonPrimitive?.content
                        respond(
                            content = """{"request_uri":"urn:example:request","expires_in":60}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                            CLIENT_ATTESTATION_CHALLENGE to listOf("challenge-2"),
                            ),
                        )
                    }
                    TOKEN_ENDPOINT -> {
                        tokenPopChallenge = jwtPart(
                            requireNotNull(request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP]),
                            1,
                        )["challenge"]?.jsonPrimitive?.content
                        respond(
                            content = """{"access_token":"access","token_type":"Bearer"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                            CLIENT_ATTESTATION_CHALLENGE to listOf("challenge-3"),
                            ),
                        )
                    }
                    CREDENTIAL_ENDPOINT -> jsonResponse(
                        """{"transaction_id":"transaction-1","interval":5}""",
                        HttpStatusCode.Accepted,
                    )
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
            attestationAssembler = ClientAttestationAssembler(StaticAttestationProvider()),
            sessionStore = sessionStore,
        )

        val session = service.start(authRequest())
        val authorization = service.beginAuthorization(session.id)

        assertEquals(listOf(CHALLENGE_ENDPOINT), challengeRequests)
        assertEquals("challenge-1", parPopChallenge)
        assertTrue(sessionStore.records.values.single().payload.contains("challenge-2"))

        val outcome = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(session.id, callback(authorization, "auth-code")),
        )

        assertIs<WalletIssuanceOutcome.Deferred>(outcome)
        assertEquals("challenge-2", tokenPopChallenge)
    }

    @Test
    fun tokenAttestationChallengeErrorRetriesWithFreshPop() = runTest {
        var tokenCalls = 0
        val tokenPopChallenges = mutableListOf<String?>()
        val provider = StaticAttestationProvider()
        val events = mutableListOf<WalletSessionEvent>()
        val service = service(
            handler = { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(attestedAuthorizationServerMetadata())
                    CHALLENGE_ENDPOINT -> jsonResponse("""{"attestation_challenge":"challenge-1"}""")
                    TOKEN_ENDPOINT -> {
                        tokenCalls += 1
                        tokenPopChallenges += jwtPart(
                            requireNotNull(request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP]),
                            1,
                        )["challenge"]?.jsonPrimitive?.content
                        if (tokenCalls == 1) {
                            respond(
                                content = """{"error":"use_attestation_challenge"}""",
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(
                                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                    CLIENT_ATTESTATION_CHALLENGE to listOf("challenge-2"),
                                ),
                            )
                        } else {
                            jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                        }
                    }
                    CREDENTIAL_ENDPOINT -> jsonResponse(
                        """{"transaction_id":"transaction-1","interval":5}""",
                        HttpStatusCode.Accepted,
                    )
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
            attestationAssembler = ClientAttestationAssembler(provider),
            onEvent = { events += it },
        )

        val outcome = service.continuePreAuthorized(service.start(preAuthorizedRequest()).id)

        assertIs<WalletIssuanceOutcome.Deferred>(outcome)
        assertEquals(listOf<String?>("challenge-1", "challenge-2"), tokenPopChallenges)
        assertEquals(1, provider.calls)
        assertEquals(1, events.count { it == WalletSessionEvent.issuance_attestation_obtained })
    }

    @Test
    fun tokenRedirectReusesAttestationAndRegeneratesOnlyPop() = runTest {
        val provider = StaticAttestationProvider()
        val attestationJwts = mutableListOf<String>()
        val popJwts = mutableListOf<String>()
        val events = mutableListOf<WalletSessionEvent>()
        val service = service(
            handler = { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(attestedAuthorizationServerMetadata())
                    CHALLENGE_ENDPOINT -> jsonResponse("""{"attestation_challenge":"challenge-1"}""")
                    TOKEN_ENDPOINT, REDIRECTED_TOKEN_ENDPOINT -> {
                        attestationJwts += requireNotNull(
                            request.headers[ClientAttestationHeaders.HEADER_ATTESTATION]
                        )
                        popJwts += requireNotNull(
                            request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP]
                        )
                        if (request.url.toString() == TOKEN_ENDPOINT) {
                            respond(
                                content = "",
                                status = HttpStatusCode.TemporaryRedirect,
                                headers = headersOf(HttpHeaders.Location, REDIRECTED_TOKEN_ENDPOINT),
                            )
                        } else {
                            jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                        }
                    }
                    CREDENTIAL_ENDPOINT -> jsonResponse(
                        """{"transaction_id":"transaction-1","interval":5}""",
                        HttpStatusCode.Accepted,
                    )
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
            attestationAssembler = ClientAttestationAssembler(provider),
            onEvent = { events += it },
        )

        val outcome = service.continuePreAuthorized(service.start(preAuthorizedRequest()).id)

        assertIs<WalletIssuanceOutcome.Deferred>(outcome)
        assertEquals(2, attestationJwts.size)
        assertEquals(1, attestationJwts.distinct().size)
        assertEquals(2, popJwts.distinct().size)
        assertEquals(1, provider.calls)
        assertEquals(1, events.count { it == WalletSessionEvent.issuance_attestation_obtained })
    }

    @Test
    fun rejectsUnsupportedAdvertisedClientAttestationAlgorithmsBeforeSendingPar() = runTest {
        var parCalls = 0
        val service = service(
            handler = { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(
                        attestedAuthorizationServerMetadata().replace(
                            "\"client_attestation_pop_signing_alg_values_supported\":[\"ES256\"]",
                            "\"client_attestation_pop_signing_alg_values_supported\":[\"RS256\"]",
                        )
                    )
                    PAR_ENDPOINT -> {
                        parCalls++
                        respondError(HttpStatusCode.InternalServerError)
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
            attestationAssembler = ClientAttestationAssembler(StaticAttestationProvider()),
        )

        val session = service.start(authRequest())
        assertFailsWith<IllegalArgumentException> { service.beginAuthorization(session.id) }
        assertEquals(0, parCalls)
    }

    @Test
    fun doesNotValidateAttestationAlgorithmsWhenClientAttestationIsNotConfigured() = runTest {
        var parCalls = 0
        val service = service(
            handler = { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(
                        attestedAuthorizationServerMetadata().replace(
                            "\"client_attestation_pop_signing_alg_values_supported\":[\"ES256\"]",
                            "\"client_attestation_pop_signing_alg_values_supported\":[\"RS256\"]",
                        )
                    )
                    PAR_ENDPOINT -> {
                        parCalls += 1
                        assertEquals(null, request.headers[ClientAttestationHeaders.HEADER_ATTESTATION])
                        assertEquals(null, request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP])
                        jsonResponse(
                            """{"request_uri":"urn:example:request","expires_in":60}""",
                            HttpStatusCode.Created,
                        )
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
        )

        service.beginAuthorization(service.start(authRequest()).id)

        assertEquals(1, parCalls)
    }

    @Test
    fun rejectsWalletAttestationWhenItsActualAlgorithmIsNotAdvertised() = runTest {
        var parCalls = 0
        val service = service(
            handler = { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(attestedAuthorizationServerMetadata())
                    CHALLENGE_ENDPOINT -> jsonResponse("""{"attestation_challenge":"challenge-1"}""")
                    PAR_ENDPOINT -> {
                        parCalls += 1
                        respondError(HttpStatusCode.InternalServerError)
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
            attestationAssembler = ClientAttestationAssembler(
                StaticAttestationProvider("eyJhbGciOiJSUzI1NiJ9.e30.c2ln"),
            ),
        )

        val session = service.start(authRequest())
        assertFailsWith<IllegalArgumentException> { service.beginAuthorization(session.id) }
        assertEquals(0, parCalls)
    }

    @Test
    fun acceptsWalletAttestationWhenItsActualAlgorithmIsAdvertised() = runTest {
        var parCalls = 0
        val service = service(
            handler = { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(
                        attestedAuthorizationServerMetadata().replace(
                            "\"client_attestation_signing_alg_values_supported\":[\"ES256\"]",
                            "\"client_attestation_signing_alg_values_supported\":[\"RS256\"]",
                        )
                    )
                    CHALLENGE_ENDPOINT -> jsonResponse("""{"attestation_challenge":"challenge-1"}""")
                    PAR_ENDPOINT -> {
                        parCalls += 1
                        jsonResponse(
                            """{"request_uri":"urn:example:request","expires_in":60}""",
                            HttpStatusCode.Created,
                        )
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
            attestationAssembler = ClientAttestationAssembler(
                StaticAttestationProvider("eyJhbGciOiJSUzI1NiJ9.e30.c2ln"),
            ),
        )

        service.beginAuthorization(service.start(authRequest()).id)

        assertEquals(1, parCalls)
    }

    @Test
    fun doesNotFetchAttestationChallengeWhenClientAttestationIsNotConfigured() = runTest {
        var challengeCalls = 0
        val service = service(handler = { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(attestedAuthorizationServerMetadata())
                CHALLENGE_ENDPOINT -> {
                    challengeCalls++
                    jsonResponse("""{"attestation_challenge":"must-not-be-requested"}""")
                }
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":5}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        })

        val outcome = service.continuePreAuthorized(service.start(preAuthorizedRequest()).id)

        assertIs<WalletIssuanceOutcome.Deferred>(outcome)
        assertEquals(0, challengeCalls)
    }

    @Test
    fun authorizationCallbackIsBoundToSessionStateAndRedirect() = runTest {
        var tokenCalls = 0
        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata())
                TOKEN_ENDPOINT -> {
                    tokenCalls += 1
                    jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                }
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":5}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val first = service.start(authRequest())
        val second = service.start(authRequest())
        val firstAuthorization = service.beginAuthorization(first.id)
        service.beginAuthorization(second.id)
        val crossBound = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(
                sessionId = second.id,
                callbackUri = callback(firstAuthorization, code = "cross-bound"),
            )
        )
        assertEquals(WalletIssuanceErrorCode.INVALID_CALLBACK, assertIs<WalletIssuanceOutcome.Failed>(crossBound).error.code)
        assertEquals(0, tokenCalls)

        val wrongRedirect = service.start(authRequest())
        val wrongRedirectAuthorization = service.beginAuthorization(wrongRedirect.id)
        val wrongRedirectResult = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(
                sessionId = wrongRedirect.id,
                callbackUri = "other.wallet:/callback?code=code&state=${wrongRedirectAuthorization.state}",
            )
        )
        assertEquals(
            WalletIssuanceErrorCode.INVALID_CALLBACK,
            assertIs<WalletIssuanceOutcome.Failed>(wrongRedirectResult).error.code,
        )
        assertEquals(0, tokenCalls)

        val accepted = service.start(authRequest())
        val acceptedAuthorization = service.beginAuthorization(accepted.id)
        val result = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(accepted.id, callback(acceptedAuthorization, "accepted-code"))
        )
        val deferred = assertIs<WalletIssuanceOutcome.Deferred>(result)
        assertEquals("test-credential", deferred.credentials.single().credentialConfigurationId)
        assertEquals(1, tokenCalls)

        val replay = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(accepted.id, callback(acceptedAuthorization, "replayed-code"))
        )
        assertEquals(WalletIssuanceErrorCode.INVALID_SESSION, assertIs<WalletIssuanceOutcome.Failed>(replay).error.code)
        assertEquals(1, tokenCalls)
    }

    @Test
    fun authorizationDenialAndExplicitCancellationReturnTypedCancellation() = runTest {
        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata())
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val denied = service.start(authRequest())
        val deniedAuthorization = service.beginAuthorization(denied.id)
        assertIs<WalletIssuanceOutcome.Cancelled>(
            service.continueAuthorization(
                WalletIssuanceAuthorizationCallback(
                    denied.id,
                    "wallet.example:/callback?error=access_denied&state=${deniedAuthorization.state}",
                )
            )
        )
        val cancelled = service.start(authRequest())
        assertIs<WalletIssuanceOutcome.Cancelled>(service.cancel(cancelled.id))
        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(service.cancel(cancelled.id)).error.code,
        )
    }

    @Test
    fun authorizationCallbackHonorsAuthorizationServerIssuerMetadata() = runTest {
        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(responseIssuer = true))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1"}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val missing = service.start(authRequest())
        val missingAuthorization = service.beginAuthorization(missing.id)
        assertEquals(
            WalletIssuanceErrorCode.INVALID_CALLBACK,
            assertIs<WalletIssuanceOutcome.Failed>(
                service.continueAuthorization(
                    WalletIssuanceAuthorizationCallback(missing.id, callback(missingAuthorization, "missing-iss"))
                )
            ).error.code,
        )

        val mismatched = service.start(authRequest())
        val mismatchedAuthorization = service.beginAuthorization(mismatched.id)
        assertEquals(
            WalletIssuanceErrorCode.INVALID_CALLBACK,
            assertIs<WalletIssuanceOutcome.Failed>(
                service.continueAuthorization(
                    WalletIssuanceAuthorizationCallback(
                        mismatched.id,
                        "${callback(mismatchedAuthorization, "wrong-iss")}&iss=https%3A%2F%2Fother.example",
                    )
                )
            ).error.code,
        )

        val accepted = service.start(authRequest())
        val acceptedAuthorization = service.beginAuthorization(accepted.id)
        assertIs<WalletIssuanceOutcome.Deferred>(
            service.continueAuthorization(
                WalletIssuanceAuthorizationCallback(
                    accepted.id,
                    "${callback(acceptedAuthorization, "accepted")}&iss=https%3A%2F%2Fissuer.example",
                )
            )
        )
    }

    @Test
    fun strictProofPathUsesNonceEndpointSelectedKeyAndDpop() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        var nonceCalls = 0
        var credentialCalls = 0
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = true))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(dpop = true, authorizationCode = false))
                TOKEN_ENDPOINT -> {
                    assertNotNull(request.headers["DPoP"])
                    jsonResponse(
                        """{"access_token":"access-token","token_type":"DPoP","c_nonce":"legacy-token-nonce"}"""
                    )
                }
                NONCE_ENDPOINT -> {
                    nonceCalls += 1
                    assertEquals(null, request.headers[HttpHeaders.Authorization])
                    assertEquals(null, request.headers["DPoP"])
                    jsonResponse("""{"c_nonce":"endpoint-nonce"}""")
                }
                CREDENTIAL_ENDPOINT -> {
                    credentialCalls += 1
                    val body = Json.parseToJsonElement(request.bodyText()).jsonObject
                    val proof = body["proofs"]!!.jsonObject["jwt"]!!.jsonArray.single().jsonPrimitive.content
                    val proofHeader = jwtPart(proof, 0)
                    val proofPayload = jwtPart(proof, 1)
                    assertEquals("endpoint-nonce", proofPayload["nonce"]?.jsonPrimitive?.content)
                    assertEquals(ISSUER, proofPayload["aud"]?.jsonPrimitive?.content)
                    assertEquals(
                        Json.parseToJsonElement(key.getPublicKey().exportJWK()).jsonObject,
                        proofHeader["jwk"],
                    )
                    val dpop = assertNotNull(request.headers["DPoP"])
                    val dpopPayload = jwtPart(dpop, 1)
                    assertNotNull(dpopPayload["ath"])
                    if (credentialCalls == 1) {
                        respond(
                            content = "{}",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                HttpHeaders.WWWAuthenticate to listOf("DPoP error=\"use_dpop_nonce\""),
                                "DPoP-Nonce" to listOf("resource-nonce"),
                            ),
                        )
                    } else {
                        assertEquals("resource-nonce", dpopPayload["nonce"]?.jsonPrimitive?.content)
                        jsonResponse(
                            """{"transaction_id":"transaction-1","interval":7}""",
                            HttpStatusCode.Accepted,
                        )
                    }
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(Wallet("test", staticKey = key), httpClient = client)
        val session = service.start(preAuthorizedRequest())
        val result = service.continuePreAuthorized(session.id)

        val deferred = assertIs<WalletIssuanceOutcome.Deferred>(result)
        assertEquals(7, deferred.credentials.single().intervalSeconds)
        assertEquals(1, nonceCalls)
        assertEquals(2, credentialCalls)
        assertIs<WalletIssuanceOutcome.Cancelled>(service.cancel(deferred.sessionId))
        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(
                service.resumeDeferred(deferred.credentials.single().id)
            ).error.code,
        )
    }

    @Test
    fun proofRequiredWithoutNonceEndpointOmitsNonceAndIgnoresTokenNonce() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        var credentialCalls = 0
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = true, nonceEndpoint = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse(
                    """{"access_token":"must-not-be-a-proof-nonce","token_type":"Bearer","c_nonce":"legacy-token-nonce"}"""
                )
                CREDENTIAL_ENDPOINT -> {
                    credentialCalls += 1
                    val body = Json.parseToJsonElement(request.bodyText()).jsonObject
                    val proof = body["proofs"]!!.jsonObject["jwt"]!!.jsonArray.single().jsonPrimitive.content
                    val proofPayload = jwtPart(proof, 1)
                    assertEquals(null, proofPayload["nonce"])
                    assertEquals(ISSUER, proofPayload["aud"]?.jsonPrimitive?.content)
                    jsonResponse(
                        """{"transaction_id":"transaction-1","interval":7}""",
                        HttpStatusCode.Accepted,
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(Wallet("test", staticKey = key), httpClient = client)

        val session = service.start(preAuthorizedRequest())
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
        assertEquals(1, credentialCalls)
    }

    @Test
    fun authorizationSessionSurvivesServiceRecreation() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val records = RecordingSessionStore()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata())
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":5}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val wallet = Wallet("durable", staticKey = key)
        val startedBy = WalletIssuanceSessionService(wallet, sessionStore = records, httpClient = client)
        val session = startedBy.start(authRequest())
        val authorization = startedBy.beginAuthorization(session.id)
        assertTrue("codeVerifier" !in Json.encodeToString(session))

        val resumedBy = WalletIssuanceSessionService(wallet, sessionStore = records, httpClient = client)
        val outcome = resumedBy.continueAuthorization(
            WalletIssuanceAuthorizationCallback(session.id, callback(authorization, "after-recreation"))
        )

        assertIs<WalletIssuanceOutcome.Deferred>(outcome)
        assertTrue(records.records.values.any {
            it.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL && it.sessionId == session.id
        })
        assertTrue(records.records.values.none {
            it.kind == WalletIssuanceSessionRecordKind.ACTIVE_SESSION && it.sessionId == session.id
        })
    }

    @Test
    fun cancellationWhilePersistingTransitionInvalidatesSession() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val records = BlockingTransitionSessionStore()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet("transition-cancellation", staticKey = key),
            sessionStore = records,
            httpClient = client,
        )
        val session = service.start(preAuthorizedRequest())

        val continuation = async { service.continuePreAuthorized(session.id) }
        records.processingWriteStarted.await()
        continuation.cancel()

        assertFailsWith<CancellationException> { continuation.await() }
        assertTrue(records.records.isEmpty())
        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(service.continuePreAuthorized(session.id)).error.code,
        )
    }

    @Test
    fun processingSessionIsDiscardedAfterServiceRecreation() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val records = RecordingSessionStore()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val wallet = Wallet("interrupted-transition", staticKey = key)
        val session = WalletIssuanceSessionService(wallet, sessionStore = records, httpClient = client)
            .start(preAuthorizedRequest())
        val active = records.records.values.single()
        val processingPayload = active.payload.replace("\"state\":\"AWAITING_ACCEPTANCE\"", "\"state\":\"PROCESSING\"")
        assertTrue(processingPayload != active.payload)
        records.records[active.id] = active.copy(payload = processingPayload)

        val restored = WalletIssuanceSessionService(wallet, sessionStore = records, httpClient = client)
        val outcome = restored.continuePreAuthorized(session.id)

        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(outcome).error.code,
        )
        assertTrue(records.records.isEmpty())
    }

    @Test
    fun clearSessionsRemovesActiveAndDeferredContinuations() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val records = RecordingSessionStore()
        var authorizationServerMetadataCalls = 0
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(
                    authorizationServerMetadata(authorizationCode = authorizationServerMetadataCalls++ == 0)
                )
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":5}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet("clear-sessions", staticKey = key),
            sessionStore = records,
            httpClient = client,
        )
        val authorization = service.start(authRequest())
        val authorizationRequest = service.beginAuthorization(authorization.id)
        val preAuthorized = service.start(preAuthorizedRequest())
        val deferred = assertIs<WalletIssuanceOutcome.Deferred>(
            service.continuePreAuthorized(preAuthorized.id)
        )
        assertEquals(
            setOf(
                WalletIssuanceSessionRecordKind.ACTIVE_SESSION,
                WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL,
            ),
            records.records.values.map { it.kind }.toSet(),
        )

        service.clearSessions()

        assertTrue(records.records.isEmpty())
        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(
                service.continueAuthorization(
                    WalletIssuanceAuthorizationCallback(
                        authorization.id,
                        callback(authorizationRequest, "after-clear"),
                    )
                )
            ).error.code,
        )
        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(
                service.resumeDeferred(deferred.credentials.single().id)
            ).error.code,
        )
    }

    @Test
    fun deferredCredentialCanBeResumedAndStored() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val credential = key.signJws(
            """{"iss":"https://issuer.example","sub":"did:key:holder","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","TestCredential"],"credentialSubject":{"id":"did:key:holder"}}}"""
                .encodeToByteArray()
        )
        val credentialStore = RecordingCredentialStore()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":1}""",
                    HttpStatusCode.Accepted,
                )
                DEFERRED_ENDPOINT -> jsonResponse(
                    """{"credentials":[{"credential":${Json.encodeToString(credential)}}]}"""
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("deferred", staticKey = key, credentialStores = listOf(credentialStore)),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest())
        val deferred = assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
        val stored = assertIs<WalletIssuanceOutcome.Stored>(
            service.resumeDeferred(deferred.credentials.single().id)
        )

        assertEquals(stored.credentialIds, credentialStore.credentials.map { it.id })
    }

    @Test
    fun localizedIssuancePreviewAndDeferredLabelStayConsistentAfterServiceRecreation() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val credential = key.signJws(
            """{"iss":"https://issuer.example","sub":"did:key:holder","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","TestCredential"],"credentialSubject":{"id":"did:key:holder"}}}"""
                .encodeToByteArray()
        )
        val records = RecordingSessionStore()
        val credentialStore = RecordingCredentialStore()
        val localizedMetadata = issuerMetadata(proofRequired = false)
            .replace(
                "\"credential_endpoint\":\"$CREDENTIAL_ENDPOINT\",",
                "\"display\":[{\"name\":\"English Issuer\",\"locale\":\"en\"},{\"name\":\"Deutscher Aussteller\",\"locale\":\"de\",\"logo\":{\"uri\":\"https://issuer.example/de.png\",\"alt_text\":\"Aussteller-Logo\"}}],\"credential_endpoint\":\"$CREDENTIAL_ENDPOINT\",",
            )
            .replace(
                "\"credential_definition\":{\"type\":[\"VerifiableCredential\",\"TestCredential\"]}",
                "\"credential_metadata\":{\"display\":[{\"name\":\"English Credential\",\"locale\":\"en\",\"description\":\"English description\"},{\"name\":\"Deutscher Nachweis\",\"locale\":\"de\",\"description\":\"Deutsche Beschreibung\",\"logo\":{\"uri\":\"https://issuer.example/credential-de.png\",\"alt_text\":\"Nachweis-Logo\"}}]},\"credential_definition\":{\"type\":[\"VerifiableCredential\",\"TestCredential\"]}",
            )
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> {
                    assertEquals("de-AT", request.headers[HttpHeaders.AcceptLanguage])
                    jsonResponse(localizedMetadata)
                }
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":1}""",
                    HttpStatusCode.Accepted,
                )
                DEFERRED_ENDPOINT -> jsonResponse(
                    """{"credentials":[{"credential":${Json.encodeToString(credential)}}]}""",
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val wallet = Wallet("localized", staticKey = key, credentialStores = listOf(credentialStore))
        val startedBy = WalletIssuanceSessionService(wallet, sessionStore = records, httpClient = client)

        val session = startedBy.start(preAuthorizedRequest(), preferredLocales = listOf("de-AT"))
        assertEquals("Deutscher Aussteller", session.offer.issuer.name)
        assertEquals("https://issuer.example/de.png", session.offer.issuer.logoUri)
        assertEquals("Deutscher Nachweis", session.offer.credentials.single().name)
        assertEquals("Deutsche Beschreibung", session.offer.credentials.single().descriptionText)
        assertEquals("Nachweis-Logo", session.offer.credentials.single().logoAltText)
        assertFalse(records.records.values.single().payload.contains("preferredLocales"))

        val resumedBy = WalletIssuanceSessionService(wallet, sessionStore = records, httpClient = client)
        val deferred = assertIs<WalletIssuanceOutcome.Deferred>(resumedBy.continuePreAuthorized(session.id))
        assertIs<WalletIssuanceOutcome.Stored>(resumedBy.resumeDeferred(deferred.credentials.single().id))

        assertEquals("Deutscher Nachweis", credentialStore.credentials.single().label)
    }

    @Test
    fun mdocPreviewSelectsLocalizedNameLogoAltTextAndDescription() = runTest {
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> {
                    assertEquals("de-AT", request.headers[HttpHeaders.AcceptLanguage])
                    jsonResponse(
                        """
                        {
                          "credential_issuer":"$ISSUER",
                          "credential_endpoint":"$CREDENTIAL_ENDPOINT",
                          "credential_configurations_supported":{
                            "mdl":{
                              "format":"mso_mdoc",
                              "doctype":"org.iso.18013.5.1.mDL",
                              "credential_metadata":{"display":[
                                {"name":"Mobile Driving Licence","locale":"en","description":"English description","logo":{"uri":"https://issuer.example/mdl-en.png","alt_text":"English licence logo"}},
                                {"name":"Mobiler Fuehrerschein","locale":"de","description":"Deutsche Beschreibung","logo":{"uri":"https://issuer.example/mdl-de.png","alt_text":"Fuehrerschein-Logo"}}
                              ]}
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                }
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("localized-mdoc", staticKey = JWKKey.generate(KeyType.secp256r1)),
            httpClient = client,
        )

        val preview = service.start(preAuthorizedRequest(configurationIds = listOf("mdl")), listOf("de-AT"))
        val credential = preview.offer.credentials.single()

        assertEquals("mso_mdoc", credential.format)
        assertEquals("org.iso.18013.5.1.mDL", credential.doctype)
        assertEquals("Mobiler Fuehrerschein", credential.name)
        assertEquals("Deutsche Beschreibung", credential.descriptionText)
        assertEquals("https://issuer.example/mdl-de.png", credential.logoUri)
        assertEquals("Fuehrerschein-Logo", credential.logoAltText)
    }

    @Test
    fun failedMultiCredentialRequestDoesNotStrandDeferredContinuation() = runTest {
        val records = RecordingSessionStore()
        var credentialCalls = 0
        val key = JWKKey.generate(KeyType.secp256r1)
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(
                    issuerMetadata(false, configurationIds = listOf("first", "second"))
                )
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> {
                    credentialCalls += 1
                    if (credentialCalls == 1) {
                        jsonResponse("""{"transaction_id":"transaction-1"}""", HttpStatusCode.Accepted)
                    } else {
                        jsonResponse("""{"error":"invalid_credential_request"}""", HttpStatusCode.BadRequest)
                    }
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("atomic", staticKey = key),
            sessionStore = records,
            httpClient = client,
        )
        val request = preAuthorizedRequest(configurationIds = listOf("first", "second"))

        assertIs<WalletIssuanceOutcome.Failed>(
            service.continuePreAuthorized(service.start(request).id)
        )
        assertEquals(2, credentialCalls)
        assertTrue(records.records.values.none { it.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL })
    }

    @Test
    fun cancellationWhilePublishingResolvedEventDoesNotStrandSession() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val records = RecordingSessionStore()
        val eventStarted = CompletableDeferred<Unit>()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet("event-cancellation", staticKey = key),
            onEvent = {
                eventStarted.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            },
            sessionStore = records,
            httpClient = client,
        )

        val start = async { service.start(preAuthorizedRequest()) }
        eventStarted.await()
        start.cancel()

        assertFailsWith<CancellationException> { start.await() }
        assertTrue(records.records.isEmpty())
    }

    @Test
    fun coroutineCancellationIsNotConvertedIntoAProtocolFailure() = runTest {
        val tokenStarted = CompletableDeferred<Unit>()
        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> {
                    tokenStarted.complete(Unit)
                    kotlinx.coroutines.awaitCancellation()
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val session = service.start(preAuthorizedRequest())
        val continuation = async { service.continuePreAuthorized(session.id) }
        tokenStarted.await()
        continuation.cancel()

        assertFailsWith<CancellationException> { continuation.await() }
        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(service.continuePreAuthorized(session.id)).error.code,
        )
    }

    @Test
    fun proofPrefersHolderDidBoundToSelectedKey() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val holderDid = "did:jwk:holder"
        val holderDidKeyId = "$holderDid#0"
        val didStore = InMemoryDidStore().also { store ->
            store.addDid(
                WalletDidEntry(
                    did = holderDid,
                    document = didDocument(holderDid, holderDidKeyId, key),
                )
            )
        }
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(
                    issuerMetadata(proofRequired = true)
                        .replace(
                            "\"cryptographic_binding_methods_supported\":[\"jwk\"]",
                            "\"cryptographic_binding_methods_supported\":[\"jwk\",\"did:jwk\"]",
                        )
                )
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                NONCE_ENDPOINT -> jsonResponse("""{"c_nonce":"endpoint-nonce"}""")
                CREDENTIAL_ENDPOINT -> {
                    val body = Json.parseToJsonElement(request.bodyText()).jsonObject
                    val proof = body["proofs"]!!.jsonObject["jwt"]!!.jsonArray.single().jsonPrimitive.content
                    val proofHeader = jwtPart(proof, 0)
                    assertEquals(holderDidKeyId, proofHeader["kid"]?.jsonPrimitive?.content)
                    assertEquals(null, proofHeader["jwk"])
                    jsonResponse("""{"transaction_id":"transaction-1"}""", HttpStatusCode.Accepted)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet("test", staticKey = key, didStore = didStore),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest().copy(did = holderDid))
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
    }

    @Test
    fun proofNeverUsesHolderDidBoundToAnotherKey() = runTest {
        val selectedKey = JWKKey.generate(KeyType.secp256r1)
        val otherKey = JWKKey.generate(KeyType.secp256r1)
        val holderDid = "did:jwk:other-holder"
        val didStore = InMemoryDidStore().also { store ->
            store.addDid(
                WalletDidEntry(
                    did = holderDid,
                    document = didDocument(holderDid, "$holderDid#0", otherKey),
                )
            )
        }
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(
                    issuerMetadata(proofRequired = true)
                        .replace(
                            "\"cryptographic_binding_methods_supported\":[\"jwk\"]",
                            "\"cryptographic_binding_methods_supported\":[\"jwk\",\"did:jwk\"]",
                        )
                )
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                NONCE_ENDPOINT -> jsonResponse("""{"c_nonce":"endpoint-nonce"}""")
                CREDENTIAL_ENDPOINT -> {
                    val body = Json.parseToJsonElement(request.bodyText()).jsonObject
                    val proof = body["proofs"]!!.jsonObject["jwt"]!!.jsonArray.single().jsonPrimitive.content
                    val proofHeader = jwtPart(proof, 0)
                    assertEquals(null, proofHeader["kid"])
                    assertEquals(
                        Json.parseToJsonElement(selectedKey.getPublicKey().exportJWK()).jsonObject,
                        proofHeader["jwk"],
                    )
                    jsonResponse("""{"transaction_id":"transaction-1"}""", HttpStatusCode.Accepted)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet("test", staticKey = selectedKey, didStore = didStore),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest().copy(did = holderDid))
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
    }

    @Test
    fun immediateCredentialIsParsedStoredAndReturned() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val credential = key.signJws(
            """{"iss":"https://issuer.example","sub":"did:key:holder","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","TestCredential"],"credentialSubject":{"id":"did:key:holder","given_name":"Ada"}}}"""
                .encodeToByteArray()
        )
        val store = RecordingCredentialStore()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    buildJsonObject {
                        put(
                            "credentials",
                            Json.parseToJsonElement("""[{"credential":${Json.encodeToString(credential)}}]"""),
                        )
                    }.toString()
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = key, credentialStores = listOf(store)),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest())
        val result = assertIs<WalletIssuanceOutcome.Stored>(service.continuePreAuthorized(session.id))

        assertEquals(1, result.credentialIds.size)
        assertEquals(result.credentialIds, store.credentials.map { it.id })
    }

    @Test
    fun issuancePreviewAndStoredMetadataExposeCredentialCardDisplay() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val credential = key.signJws(
            """{"iss":"https://issuer.example","sub":"did:key:holder","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","TestCredential"],"credentialSubject":{"id":"did:key:holder","given_name":"Ada"}}}"""
                .encodeToByteArray()
        )
        val store = RecordingCredentialStore()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadataWithCredentialDisplay())
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    buildJsonObject {
                        put(
                            "credentials",
                            Json.parseToJsonElement("""[{"credential":${Json.encodeToString(credential)}}]"""),
                        )
                    }.toString()
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = key, credentialStores = listOf(store)),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest())
        val preview = session.offer.credentials.single()
        assertEquals("Personal ID", preview.name)
        assertEquals("#12107c", preview.backgroundColor)
        assertEquals("https://issuer.example/pid-bg.png", preview.backgroundImageUri)
        assertEquals("#FFFFFF", preview.textColor)
        assertEquals("https://issuer.example/pid.png", preview.logoUri)

        val result = assertIs<WalletIssuanceOutcome.Stored>(service.continuePreAuthorized(session.id))
        val stored = store.credentials.single { it.id == result.credentialIds.single() }
        val credentialDisplay = stored.metadata!!
            .getValue("credentialDisplay")
            .jsonArray
            .single()
            .jsonObject
        assertEquals("Personal ID", credentialDisplay.getValue("name").jsonPrimitive.content)
        assertEquals("#12107c", credentialDisplay.getValue("background_color").jsonPrimitive.content)
        assertEquals(
            "https://issuer.example/pid-bg.png",
            credentialDisplay.getValue("background_image").jsonObject.getValue("uri").jsonPrimitive.content,
        )
    }

    @Test
    fun immediateResponseStoresW3cJwtSdJwtVcAndMdocWithoutAppParsing() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val w3cJwt = key.signJws(
            """{"iss":"https://issuer.example","sub":"did:key:holder","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","TestCredential"],"credentialSubject":{"id":"did:key:holder"}}}"""
                .encodeToByteArray()
        )
        val issuedCredentials = listOf(
            w3cJwt,
            SdJwtExamples.sdJwtVcSignedExample2,
            MdocsExamples.mdocsExampleBase64Url,
        )
        val store = RecordingCredentialStore()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    buildJsonObject {
                        put("credentials", buildJsonArray {
                            issuedCredentials.forEach { credential ->
                                add(buildJsonObject { put("credential", credential) })
                            }
                        })
                    }.toString()
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = key, credentialStores = listOf(store)),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest())
        val result = assertIs<WalletIssuanceOutcome.Stored>(service.continuePreAuthorized(session.id))

        assertEquals(3, result.credentialIds.size)
        assertEquals(3, store.credentials.size)
        assertEquals(setOf("jwt_vc_json", "dc+sd-jwt", "mso_mdoc"), store.credentials.map { it.credential.format }.toSet())
    }

    @Test
    fun advertisedDpopUsesTheTokenResponseToSelectResourceProtection() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = true))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(dpop = true, authorizationCode = false))
                TOKEN_ENDPOINT -> {
                    assertNotNull(request.headers["DPoP"])
                    jsonResponse("""{"access_token":"access-token","token_type":"Bearer"}""")
                }
                NONCE_ENDPOINT -> jsonResponse("""{"c_nonce":"endpoint-nonce"}""")
                CREDENTIAL_ENDPOINT -> {
                    assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                    assertEquals(null, request.headers["DPoP"])
                    assertNotNull(Json.parseToJsonElement(request.bodyText()).jsonObject["proofs"])
                    jsonResponse(
                        """{"transaction_id":"transaction-1","interval":7}""",
                        HttpStatusCode.Accepted,
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(Wallet("test", staticKey = key), httpClient = client)

        val session = service.start(preAuthorizedRequest())
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
    }

    @Test
    fun dpopIsAppliedToPreAuthorizedGrantWithoutGrantAdvertisement() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(
                    authorizationServerMetadata(
                        dpop = true,
                        authorizationCode = false,
                        advertiseSelectedGrant = false,
                    )
                )
                TOKEN_ENDPOINT -> {
                    assertNotNull(request.headers["DPoP"])
                    jsonResponse("""{"access_token":"access-token","token_type":"Bearer"}""")
                }
                CREDENTIAL_ENDPOINT -> {
                    assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                    assertEquals(null, request.headers["DPoP"])
                    jsonResponse(
                        """{"transaction_id":"transaction-1","interval":7}""",
                        HttpStatusCode.Accepted,
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(Wallet("test", staticKey = key), httpClient = client)

        val session = service.start(preAuthorizedRequest())
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
    }

    @Test
    fun ed25519PreAuthorizedGrantNegotiatesEdDsaForDpop() = runTest {
        var tokenDpop: String? = null
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(
                    authorizationServerMetadata(
                        dpop = true,
                        authorizationCode = false,
                        dpopAlgorithms = listOf("EdDSA"),
                    )
                )
                TOKEN_ENDPOINT -> {
                    tokenDpop = request.headers["DPoP"]
                    jsonResponse("""{"access_token":"access-token","token_type":"DPoP"}""")
                }
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":7}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = JWKKey.generate(KeyType.Ed25519)),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest())
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
        assertEquals("EdDSA", jwtPart(assertNotNull(tokenDpop), 0)["alg"]?.jsonPrimitive?.content)
    }

    @Test
    fun advertisedDpopAddsAuthorizationDpopJkt() = runTest {
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(dpop = true))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = JWKKey.generate(KeyType.secp256r1)),
            httpClient = client,
        )

        val session = service.start(authRequest())
        val authorization = service.beginAuthorization(session.id)
        assertNotNull(Url(authorization.url).parameters["dpop_jkt"])
    }

    @Test
    fun ed25519AuthorizationNegotiatesEdDsaForDpop() = runTest {
        var tokenDpop: String? = null
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(
                    authorizationServerMetadata(dpop = true, dpopAlgorithms = listOf("EdDSA")),
                )
                TOKEN_ENDPOINT -> {
                    tokenDpop = request.headers["DPoP"]
                    jsonResponse("""{"access_token":"access-token","token_type":"Bearer"}""")
                }
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":7}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = JWKKey.generate(KeyType.Ed25519)),
            httpClient = client,
        )

        val session = service.start(authRequest())
        val authorization = service.beginAuthorization(session.id)
        assertNotNull(Url(authorization.url).parameters["dpop_jkt"])
        assertIs<WalletIssuanceOutcome.Deferred>(
            service.continueAuthorization(
                WalletIssuanceAuthorizationCallback(session.id, callback(authorization, "authorization-code")),
            )
        )
        assertEquals("EdDSA", jwtPart(assertNotNull(tokenDpop), 0)["alg"]?.jsonPrimitive?.content)
    }

    @Test
    fun ed25519CredentialProofNegotiatesEdDsa() = runTest {
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(
                    issuerMetadata(proofRequired = true, proofAlgorithms = listOf("EdDSA")),
                )
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access-token","token_type":"Bearer"}""")
                NONCE_ENDPOINT -> jsonResponse("""{"c_nonce":"endpoint-nonce"}""")
                CREDENTIAL_ENDPOINT -> {
                    val body = Json.parseToJsonElement(request.bodyText()).jsonObject
                    val proof = body["proofs"]!!.jsonObject["jwt"]!!.jsonArray.single().jsonPrimitive.content
                    assertEquals("EdDSA", jwtPart(proof, 0)["alg"]?.jsonPrimitive?.content)
                    jsonResponse("""{"transaction_id":"transaction-1","interval":7}""", HttpStatusCode.Accepted)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = JWKKey.generate(KeyType.Ed25519)),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest())
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
    }

    @Test
    fun authorizationDpopJktMatchesTokenProofKey() = runTest {
        var tokenDpop: String? = null
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(dpop = true))
                TOKEN_ENDPOINT -> {
                    tokenDpop = request.headers["DPoP"]
                    jsonResponse("""{"access_token":"access-token","token_type":"Bearer"}""")
                }
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":7}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = JWKKey.generate(KeyType.secp256r1)),
            httpClient = client,
        )

        val session = service.start(authRequest())
        val authorization = service.beginAuthorization(session.id)
        val authorizationJkt = assertNotNull(Url(authorization.url).parameters["dpop_jkt"])
        assertIs<WalletIssuanceOutcome.Deferred>(
            service.continueAuthorization(
                WalletIssuanceAuthorizationCallback(session.id, callback(authorization, "authorization-code")),
            )
        )
        val proofJwk = assertNotNull(jwtPart(assertNotNull(tokenDpop), 0)["jwk"])
        val proofJkt = JWKKey.importJWK(proofJwk.toString()).getOrThrow().getPublicKey().getThumbprint()
        assertEquals(authorizationJkt, proofJkt)
    }

    @Test
    fun dpopIsAppliedToAuthorizationCodeWithoutGrantAdvertisement() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(
                    """{"issuer":"$ISSUER","authorization_endpoint":"$AUTHORIZATION_ENDPOINT","token_endpoint":"$TOKEN_ENDPOINT","response_types_supported":["code"],"dpop_signing_alg_values_supported":["ES256"]}"""
                )
                TOKEN_ENDPOINT -> {
                    assertNotNull(request.headers["DPoP"])
                    jsonResponse("""{"access_token":"access-token","token_type":"Bearer"}""")
                }
                CREDENTIAL_ENDPOINT -> {
                    assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                    assertEquals(null, request.headers["DPoP"])
                    jsonResponse(
                        """{"transaction_id":"transaction-1","interval":7}""",
                        HttpStatusCode.Accepted,
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(Wallet("test", staticKey = key), httpClient = client)

        val session = service.start(authRequest())
        val authorization = service.beginAuthorization(session.id)
        assertIs<WalletIssuanceOutcome.Deferred>(
            service.continueAuthorization(
                WalletIssuanceAuthorizationCallback(session.id, callback(authorization, "authorization-code"))
            )
        )
    }

    @Test
    fun unsupportedDpopKeyIsRejectedBeforePushedAuthorizationRequest() = runTest {
        var parCalls = 0
        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(
                    """{"issuer":"$ISSUER","authorization_endpoint":"$AUTHORIZATION_ENDPOINT","token_endpoint":"$TOKEN_ENDPOINT","pushed_authorization_request_endpoint":"$PAR_ENDPOINT","response_types_supported":["code"],"dpop_signing_alg_values_supported":["RS256"]}"""
                )
                PAR_ENDPOINT -> {
                    parCalls += 1
                    jsonResponse("""{"request_uri":"urn:example:par:1","expires_in":60}""", HttpStatusCode.Created)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val session = service.start(authRequest())
        assertFailsWith<IllegalArgumentException> { service.beginAuthorization(session.id) }
        assertEquals(0, parCalls)
        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(
                service.continueAuthorization(
                    WalletIssuanceAuthorizationCallback(session.id, "${REDIRECT_URI}?code=unused&state=unused")
                )
            ).error.code,
        )
    }

    @Test
    fun unsupportedDpopKeyInPreAuthorizedGrantFailsAsCryptoBeforeTokenRequest() = runTest {
        var tokenCalls = 0
        val service = WalletIssuanceSessionService(
            Wallet("test", staticKey = JWKKey.generate(KeyType.Ed25519)),
            httpClient = client { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(
                        authorizationServerMetadata(
                            dpop = true,
                            authorizationCode = false,
                            dpopAlgorithms = listOf("RS256"),
                        )
                    )
                    TOKEN_ENDPOINT -> {
                        tokenCalls += 1
                        jsonResponse("{\"access_token\":\"access\",\"token_type\":\"DPoP\"}")
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
        )

        val result = service.continuePreAuthorized(service.start(preAuthorizedRequest()).id)

        assertEquals(WalletIssuanceErrorCode.CRYPTO, assertIs<WalletIssuanceOutcome.Failed>(result).error.code)
        assertEquals(0, tokenCalls)
    }

    @Test
    fun rejectedTransactionCodeDoesNotConsumeTheSession() = runTest {
        var tokenCalls = 0
        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                TOKEN_ENDPOINT -> {
                    tokenCalls += 1
                    if (request.bodyText().contains("tx_code=wrong")) {
                        jsonResponse("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
                    } else {
                        assertTrue(request.bodyText().contains("tx_code=correct"))
                        jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                    }
                }
                CREDENTIAL_ENDPOINT -> jsonResponse(
                    """{"transaction_id":"transaction-1","interval":5}""",
                    HttpStatusCode.Accepted,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val request = WalletIssuanceSessionRequest(
            offerJson = buildJsonObject {
                put("credential_issuer", ISSUER)
                put("credential_configuration_ids", Json.parseToJsonElement("""["test-credential"]"""))
                put(
                    "grants",
                    Json.parseToJsonElement(
                        """{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code","tx_code":{"input_mode":"text","length":7}}}"""
                    ),
                )
            },
            clientId = "wallet-client",
            redirectUri = Url(REDIRECT_URI),
        )

        val session = service.start(request)
        val rejected = assertIs<WalletIssuanceOutcome.Failed>(
            service.continuePreAuthorized(session.id, "wrong")
        )
        assertEquals(WalletIssuanceErrorCode.ISSUER_RESPONSE, rejected.error.code)
        assertIs<WalletIssuanceOutcome.Deferred>(
            service.continuePreAuthorized(session.id, "correct")
        )
        assertEquals(2, tokenCalls)
    }

    @Test
    fun advertisedParIsUsedForAuthorizationRequest() = runTest {
        var parCalls = 0
        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(
                    """
                    {
                      "issuer":"$ISSUER",
                      "authorization_endpoint":"$AUTHORIZATION_ENDPOINT",
                      "token_endpoint":"$TOKEN_ENDPOINT",
                      "pushed_authorization_request_endpoint":"$PAR_ENDPOINT",
                      "require_pushed_authorization_requests":true,
                      "response_types_supported":["code"],
                      "grant_types_supported":["authorization_code"],
                      "dpop_signing_alg_values_supported":["ES256"]
                    }
                    """.trimIndent()
                )
                PAR_ENDPOINT -> {
                    parCalls += 1
                    assertTrue(request.bodyText().contains("code_challenge="))
                    assertTrue(request.bodyText().contains("dpop_jkt="))
                    jsonResponse("""{"request_uri":"urn:example:par:1","expires_in":60}""", HttpStatusCode.Created)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val session = service.start(authRequest())
        assertEquals(0, parCalls)
        val authorization = service.beginAuthorization(session.id)

        assertEquals(1, parCalls)
        assertTrue(authorization.pushedAuthorizationRequestUsed)
        assertEquals("urn:example:par:1", Url(authorization.url).parameters["request_uri"])
        assertNotNull(authorization.requestUriExpiresAtEpochMilliseconds)
    }

    @Test
    fun transientParFailuresRestoreSessionForRetry() = runTest {
        val responses = ArrayDeque(
            listOf(
                HttpStatusCode.TooManyRequests,
                HttpStatusCode.ServiceUnavailable,
                HttpStatusCode.Created,
            )
        )
        var parCalls = 0

        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA ->
                    jsonResponse(issuerMetadata(proofRequired = false))

                AS_METADATA ->
                    jsonResponse(
                        """
                        {
                          "issuer":"$ISSUER",
                          "authorization_endpoint":"$AUTHORIZATION_ENDPOINT",
                          "token_endpoint":"$TOKEN_ENDPOINT",
                          "pushed_authorization_request_endpoint":"$PAR_ENDPOINT",
                          "response_types_supported":["code"]
                        }
                        """.trimIndent()
                    )

                PAR_ENDPOINT -> {
                    parCalls += 1
                    when (val status = responses.removeFirst()) {
                        HttpStatusCode.Created ->
                            jsonResponse(
                                """{"request_uri":"urn:example:par:retry","expires_in":60}""",
                                status,
                            )

                        else ->
                            jsonResponse("""{"error":"temporarily_unavailable"}""", status)
                    }
                }

                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val session = service.start(authRequest())

        assertFailsWith<Exception> {
            service.beginAuthorization(session.id)
        }
        assertFailsWith<Exception> {
            service.beginAuthorization(session.id)
        }

        val authorization = service.beginAuthorization(session.id)

        assertTrue(authorization.pushedAuthorizationRequestUsed)
        assertEquals(3, parCalls)
    }

    @Test
    fun parRequiresCreatedResponseAndPositiveExpiry() = runTest {
        var parBody = """{"request_uri":"urn:example:par:invalid-status","expires_in":60}"""
        var parStatus = HttpStatusCode.OK
        val service = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                AS_METADATA -> jsonResponse(
                    """{"issuer":"$ISSUER","authorization_endpoint":"$AUTHORIZATION_ENDPOINT","token_endpoint":"$TOKEN_ENDPOINT","pushed_authorization_request_endpoint":"$PAR_ENDPOINT","response_types_supported":["code"]}"""
                )
                PAR_ENDPOINT -> jsonResponse(parBody, parStatus)
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val session = service.start(authRequest())
        assertFailsWith<Exception> { service.beginAuthorization(session.id) }
        assertFailsWith<IllegalStateException> { service.beginAuthorization(session.id) }

        parBody = """{"request_uri":"urn:example:par:invalid-expiry","expires_in":0}"""
        parStatus = HttpStatusCode.Created
        val second = service.start(authRequest())
        assertFailsWith<Exception> { service.beginAuthorization(second.id) }
        assertFailsWith<IllegalStateException> { service.beginAuthorization(second.id) }

        parBody = """{"request_uri":"""
        val third = service.start(authRequest())
        assertFailsWith<Exception> { service.beginAuthorization(third.id) }
        assertFailsWith<IllegalStateException> { service.beginAuthorization(third.id) }
    }

    @Test
    fun authorizationPersistenceFailureAfterCallbackTransitionRollsBackSession() = runTest {
        val records = FailingAuthorizationStateStore()
        val service = WalletIssuanceSessionService(
            wallet = Wallet("authorization-rollback", staticKey = JWKKey.generate(KeyType.secp256r1)),
            sessionStore = records,
            httpClient = client { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(authorizationServerMetadata())
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
        )

        val session = service.start(authRequest())
        assertFailsWith<AuthorizationStatePersistenceFailure> {
            service.beginAuthorization(session.id)
        }

        assertTrue(records.records.values.single().payload.contains("\"state\":\"AWAITING_ACCEPTANCE\""))
        assertNotNull(service.beginAuthorization(session.id))
    }

    @Test
    fun expiredReviewSessionIsRemovedBeforeAcceptance() = runTest {
        var current = Clock.System.now()
        val records = RecordingSessionStore()
        val service = WalletIssuanceSessionService(
            wallet = Wallet("expiry", staticKey = JWKKey.generate(KeyType.secp256r1)),
            sessionStore = records,
            httpClient = client { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(authorizationServerMetadata(authorizationCode = false))
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
            sessionPolicy = WalletIssuanceSessionPolicy(reviewTtl = 1.seconds, authorizationCallbackTtl = 1.seconds),
            now = { current },
        )
        val session = service.start(preAuthorizedRequest())
        current += 2.seconds

        val outcome = service.continuePreAuthorized(session.id)
        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(outcome).error.code,
        )
        assertTrue(records.records.isEmpty())
    }

    @Test
    fun expiredAuthorizationCallbackIsRejectedBeforeTokenExchange() = runTest {
        var current = Clock.System.now()
        var tokenCalls = 0
        val records = RecordingSessionStore()
        val service = WalletIssuanceSessionService(
            wallet = Wallet("callback-expiry", staticKey = JWKKey.generate(KeyType.secp256r1)),
            sessionStore = records,
            httpClient = client { request ->
                when (request.url.toString()) {
                    ISSUER_METADATA -> jsonResponse(issuerMetadata(proofRequired = false))
                    AS_METADATA -> jsonResponse(authorizationServerMetadata())
                    TOKEN_ENDPOINT -> {
                        tokenCalls += 1
                        jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
            sessionPolicy = WalletIssuanceSessionPolicy(
                reviewTtl = 1.seconds,
                authorizationCallbackTtl = 1.seconds,
            ),
            now = { current },
        )

        val session = service.start(authRequest())
        val authorization = service.beginAuthorization(session.id)
        current += 2.seconds

        val outcome = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(session.id, callback(authorization, "expired-code")),
        )

        assertEquals(
            WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(outcome).error.code,
        )
        assertEquals(0, tokenCalls)
        assertTrue(records.records.isEmpty())
    }

    @Test
    fun rejectsMismatchedIssuerMetadata() = runTest {
        val mismatched = service { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(
                    issuerMetadata(proofRequired = false).replace(
                        "\"credential_issuer\":\"$ISSUER\"",
                        "\"credential_issuer\":\"https://other-issuer.example\"",
                    )
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        assertFailsWith<IllegalArgumentException> { mismatched.start(preAuthorizedRequest()) }
    }

    private suspend fun service(
        attestationAssembler: ClientAttestationAssembler? = null,
        sessionStore: WalletIssuanceSessionStore? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): WalletIssuanceSessionService {
        val key = JWKKey.generate(KeyType.secp256r1)
        return WalletIssuanceSessionService(
            wallet = Wallet("test", staticKey = key),
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
            sessionStore = sessionStore,
            httpClient = client(handler),
        )
    }

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        install(ContentNegotiation) { json(json) }
    }

    private fun MockRequestHandleScope.jsonResponse(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun authRequest() = WalletIssuanceSessionRequest(
        offerJson = buildJsonObject {
            put("credential_issuer", ISSUER)
            put("credential_configuration_ids", Json.parseToJsonElement("""["test-credential"]"""))
            put("grants", Json.parseToJsonElement("""{"authorization_code":{"issuer_state":"issuer-state"}}"""))
        },
        clientId = "wallet-client",
        redirectUri = Url(REDIRECT_URI),
    )

    private fun preAuthorizedRequest(
        configurationIds: List<String> = listOf("test-credential"),
    ) = WalletIssuanceSessionRequest(
        offerJson = buildJsonObject {
            put("credential_issuer", ISSUER)
            put("credential_configuration_ids", buildJsonArray {
                configurationIds.forEach { add(Json.parseToJsonElement(Json.encodeToString(it))) }
            })
            put(
                "grants",
                Json.parseToJsonElement(
                    """{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}"""
                ),
            )
        },
        clientId = "wallet-client",
        redirectUri = Url(REDIRECT_URI),
    )

    private fun requestWithIssuer(issuer: String): WalletIssuanceSessionRequest =
        preAuthorizedRequest().copy(
            offerJson = Json.parseToJsonElement(
                preAuthorizedRequest().offerJson.toString().replace(ISSUER, issuer)
            ).jsonObject,
        )

    private fun callback(authorization: WalletIssuanceAuthorization, code: String) =
        "$REDIRECT_URI?code=$code&state=${authorization.state}"

    private fun issuerMetadata(
        proofRequired: Boolean,
        nonceEndpoint: Boolean = true,
        configurationIds: List<String> = listOf("test-credential"),
        proofAlgorithms: List<String> = listOf("ES256"),
    ): String {
        val proofAlgorithmsJson = proofAlgorithms.joinToString(",") { "\"$it\"" }
        val configurations = configurationIds.joinToString(",") { id ->
            """
            "$id":{
              "format":"jwt_vc_json",
              "credential_definition":{"type":["VerifiableCredential","TestCredential"]}
              ${if (proofRequired) ",\"cryptographic_binding_methods_supported\":[\"jwk\"],\"proof_types_supported\":{\"jwt\":{\"proof_signing_alg_values_supported\":[$proofAlgorithmsJson]}}" else ""}
            }
            """.trimIndent()
        }
        return """
        {
          "credential_issuer":"$ISSUER",
          "credential_endpoint":"$CREDENTIAL_ENDPOINT",
          ${if (nonceEndpoint) "\"nonce_endpoint\":\"$NONCE_ENDPOINT\"," else ""}
          "deferred_credential_endpoint":"$DEFERRED_ENDPOINT",
          "credential_configurations_supported":{
            $configurations
          }
        }
        """.trimIndent()
    }

    private fun issuerMetadataWithCredentialDisplay(): String = """
        {
          "credential_issuer":"$ISSUER",
          "credential_endpoint":"$CREDENTIAL_ENDPOINT",
          "display":[{"name":"Example Issuer","locale":"en"}],
          "credential_configurations_supported":{
            "test-credential":{
              "format":"jwt_vc_json",
              "credential_definition":{"type":["VerifiableCredential","TestCredential"]},
              "credential_metadata":{
                "display":[{
                  "name":"Personal ID",
                  "locale":"en",
                  "logo":{"uri":"https://issuer.example/pid.png","alt_text":"PID logo"},
                  "description":"Government identity",
                  "background_color":"#12107c",
                  "background_image":{"uri":"https://issuer.example/pid-bg.png"},
                  "text_color":"#FFFFFF"
                }]
              }
            }
          }
        }
    """.trimIndent()

    private fun attestedAuthorizationServerMetadata() = """
        {
          "issuer":"$ISSUER",
          "authorization_endpoint":"$AUTHORIZATION_ENDPOINT",
          "token_endpoint":"$TOKEN_ENDPOINT",
          "response_types_supported":["code"],
          "pushed_authorization_request_endpoint":"$PAR_ENDPOINT",
          "challenge_endpoint":"$CHALLENGE_ENDPOINT",
          "token_endpoint_auth_methods_supported":["attest_jwt_client_auth"],
          "client_attestation_signing_alg_values_supported":["ES256"],
          "client_attestation_pop_signing_alg_values_supported":["ES256"]
        }
    """.trimIndent()

    private fun authorizationServerMetadata(
        dpop: Boolean = false,
        authorizationCode: Boolean = true,
        responseIssuer: Boolean = false,
        advertiseSelectedGrant: Boolean = true,
        dpopAlgorithms: List<String>? = null,
    ) = """
        {
          "issuer":"$ISSUER",
          ${if (authorizationCode || !advertiseSelectedGrant) "\"authorization_endpoint\":\"$AUTHORIZATION_ENDPOINT\"," else ""}
          "token_endpoint":"$TOKEN_ENDPOINT",
          "response_types_supported":["code"],
          "grant_types_supported":["${if (authorizationCode || !advertiseSelectedGrant) "authorization_code" else "urn:ietf:params:oauth:grant-type:pre-authorized_code"}"]
          ${if (dpop) ",\"dpop_signing_alg_values_supported\":[${(dpopAlgorithms ?: listOf("ES256")).joinToString(",") { "\"$it\"" }}]" else ""}
          ${if (responseIssuer) ",\"authorization_response_iss_parameter_supported\":true" else ""}
        }
    """.trimIndent()

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private suspend fun didDocument(did: String, keyId: String, key: JWKKey) = buildJsonObject {
        put("id", did)
        put("verificationMethod", buildJsonArray {
            add(buildJsonObject {
                put("id", keyId)
                put("controller", did)
                put("type", "JsonWebKey2020")
                put("publicKeyJwk", Json.parseToJsonElement(key.getPublicKey().exportJWK()))
            })
        })
    }

    private fun jwtPart(jwt: String, index: Int) =
        Json.parseToJsonElement(jwt.split('.')[index].decodeFromBase64Url().decodeToString()).jsonObject

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val ISSUER_METADATA = "$ISSUER/.well-known/openid-credential-issuer"
        const val AS_METADATA = "$ISSUER/.well-known/oauth-authorization-server"
        const val AUTHORIZATION_ENDPOINT = "$ISSUER/authorize"
        const val TOKEN_ENDPOINT = "$ISSUER/token"
        const val REDIRECTED_TOKEN_ENDPOINT = "$TOKEN_ENDPOINT/redirected"
        const val CREDENTIAL_ENDPOINT = "$ISSUER/credential"
        const val NONCE_ENDPOINT = "$ISSUER/nonce"
        const val DEFERRED_ENDPOINT = "$ISSUER/deferred"
        const val PAR_ENDPOINT = "$ISSUER/par"
        const val CHALLENGE_ENDPOINT = "$ISSUER/challenge"
        const val REDIRECT_URI = "wallet.example:/callback"
    }

    @Suppress("DEPRECATION")
    private class StaticAttestationProvider(
        private val jwt: String =
            "eyJhbGciOiJFUzI1NiJ9.e30.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    ) : WalletAttestationProvider {
        var calls = 0
            private set

        override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String {
            calls += 1
            return jwt
        }
    }

    private class RecordingCredentialStore : WalletCredentialStore {
        val credentials = mutableListOf<StoredCredential>()

        override suspend fun getCredential(id: String): StoredCredential? = credentials.find { it.id == id }
        override suspend fun listCredentials(): Flow<StoredCredential> = flowOf(*credentials.toTypedArray())
        override suspend fun addCredential(entry: StoredCredential) { credentials += entry }
        override suspend fun removeCredential(id: String): Boolean = credentials.removeAll { it.id == id }
    }

    private class RecordingSessionStore : WalletIssuanceSessionStore {
        val records = linkedMapOf<String, WalletIssuanceSessionRecord>()

        override suspend fun get(id: String): WalletIssuanceSessionRecord? = records[id]
        override suspend fun list(): List<WalletIssuanceSessionRecord> = records.values.toList()
        override suspend fun put(record: WalletIssuanceSessionRecord) {
            records[record.id] = record
        }
        override suspend fun remove(id: String): Boolean = records.remove(id) != null
    }

    private class BlockingTransitionSessionStore : WalletIssuanceSessionStore {
        val records = linkedMapOf<String, WalletIssuanceSessionRecord>()
        val processingWriteStarted = CompletableDeferred<Unit>()

        override suspend fun get(id: String): WalletIssuanceSessionRecord? = records[id]
        override suspend fun list(): List<WalletIssuanceSessionRecord> = records.values.toList()
        override suspend fun put(record: WalletIssuanceSessionRecord) {
            if ("\"state\":\"PROCESSING\"" in record.payload) {
                processingWriteStarted.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
            records[record.id] = record
        }
        override suspend fun remove(id: String): Boolean = records.remove(id) != null
    }

    private class FailingAuthorizationStateStore : WalletIssuanceSessionStore {
        val records = linkedMapOf<String, WalletIssuanceSessionRecord>()
        private var failAwaitingCallback = true

        override suspend fun get(id: String): WalletIssuanceSessionRecord? = records[id]
        override suspend fun list(): List<WalletIssuanceSessionRecord> = records.values.toList()
        override suspend fun put(record: WalletIssuanceSessionRecord) {
            if (failAwaitingCallback && "\"state\":\"AWAITING_CALLBACK\"" in record.payload) {
                failAwaitingCallback = false
                throw AuthorizationStatePersistenceFailure()
            }
            records[record.id] = record
        }
        override suspend fun remove(id: String): Boolean = records.remove(id) != null
    }

    private class AuthorizationStatePersistenceFailure : Exception()
}

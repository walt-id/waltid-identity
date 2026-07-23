package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.credentials.examples.MdocsExamples
import id.walt.credentials.examples.SdJwtExamples
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalletIssuanceSessionServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

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
        val crossBound = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(
                sessionId = second.id,
                callbackUri = callback(first, code = "cross-bound"),
            )
        )
        assertEquals(WalletIssuanceErrorCode.INVALID_CALLBACK, assertIs<WalletIssuanceOutcome.Failed>(crossBound).error.code)
        assertEquals(0, tokenCalls)

        val wrongRedirect = service.start(authRequest())
        val wrongRedirectResult = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(
                sessionId = wrongRedirect.id,
                callbackUri = "other.wallet:/callback?code=code&state=${wrongRedirect.authorization!!.state}",
            )
        )
        assertEquals(
            WalletIssuanceErrorCode.INVALID_CALLBACK,
            assertIs<WalletIssuanceOutcome.Failed>(wrongRedirectResult).error.code,
        )
        assertEquals(0, tokenCalls)

        val accepted = service.start(authRequest())
        val result = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(accepted.id, callback(accepted, "accepted-code"))
        )
        val deferred = assertIs<WalletIssuanceOutcome.Deferred>(result)
        assertEquals("test-credential", deferred.credentials.single().credentialConfigurationId)
        assertEquals(1, tokenCalls)

        val replay = service.continueAuthorization(
            WalletIssuanceAuthorizationCallback(accepted.id, callback(accepted, "replayed-code"))
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
        assertIs<WalletIssuanceOutcome.Cancelled>(
            service.continueAuthorization(
                WalletIssuanceAuthorizationCallback(
                    denied.id,
                    "wallet.example:/callback?error=access_denied&state=${denied.authorization!!.state}",
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
                      "grant_types_supported":["authorization_code"]
                    }
                    """.trimIndent()
                )
                PAR_ENDPOINT -> {
                    parCalls += 1
                    assertTrue(request.bodyText().contains("code_challenge="))
                    jsonResponse("""{"request_uri":"urn:example:par:1","expires_in":60}""")
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val session = service.start(authRequest())

        assertEquals(1, parCalls)
        assertTrue(session.authorization!!.pushedAuthorizationRequestUsed)
        assertEquals("urn:example:par:1", Url(session.authorization.url).parameters["request_uri"])
    }

    @Test
    fun rejectsInsecureIssuerAndMismatchedIssuerMetadata() = runTest {
        val insecure = service { respondError(HttpStatusCode.NotFound) }
        assertFailsWith<IllegalArgumentException> {
            insecure.start(
                WalletIssuanceSessionRequest(
                    offerJson = buildJsonObject {
                        put("credential_issuer", "http://issuer.example")
                        put("credential_configuration_ids", Json.parseToJsonElement("""["test-credential"]"""))
                        put(
                            "grants",
                            Json.parseToJsonElement(
                                """{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}"""
                            ),
                        )
                    }
                )
            )
        }

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
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): WalletIssuanceSessionService {
        val key = JWKKey.generate(KeyType.secp256r1)
        return WalletIssuanceSessionService(Wallet("test", staticKey = key), httpClient = client(handler))
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

    private fun preAuthorizedRequest() = WalletIssuanceSessionRequest(
        offerJson = buildJsonObject {
            put("credential_issuer", ISSUER)
            put("credential_configuration_ids", Json.parseToJsonElement("""["test-credential"]"""))
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

    private fun callback(session: WalletIssuanceSession, code: String) =
        "$REDIRECT_URI?code=$code&state=${session.authorization!!.state}"

    private fun issuerMetadata(proofRequired: Boolean) = """
        {
          "credential_issuer":"$ISSUER",
          "credential_endpoint":"$CREDENTIAL_ENDPOINT",
          "nonce_endpoint":"$NONCE_ENDPOINT",
          "deferred_credential_endpoint":"$DEFERRED_ENDPOINT",
          "credential_configurations_supported":{
            "test-credential":{
              "format":"jwt_vc_json",
              "credential_definition":{"type":["VerifiableCredential","TestCredential"]}
              ${if (proofRequired) ",\"cryptographic_binding_methods_supported\":[\"jwk\"],\"proof_types_supported\":{\"jwt\":{\"proof_signing_alg_values_supported\":[\"ES256\"]}}" else ""}
            }
          }
        }
    """.trimIndent()

    private fun authorizationServerMetadata(
        dpop: Boolean = false,
        authorizationCode: Boolean = true,
    ) = """
        {
          "issuer":"$ISSUER",
          ${if (authorizationCode) "\"authorization_endpoint\":\"$AUTHORIZATION_ENDPOINT\"," else ""}
          "token_endpoint":"$TOKEN_ENDPOINT",
          "response_types_supported":["code"],
          "grant_types_supported":["${if (authorizationCode) "authorization_code" else "urn:ietf:params:oauth:grant-type:pre-authorized_code"}"]
          ${if (dpop) ",\"dpop_signing_alg_values_supported\":[\"ES256\"]" else ""}
        }
    """.trimIndent()

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun jwtPart(jwt: String, index: Int) =
        Json.parseToJsonElement(jwt.split('.')[index].decodeFromBase64Url().decodeToString()).jsonObject

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val ISSUER_METADATA = "$ISSUER/.well-known/openid-credential-issuer"
        const val AS_METADATA = "$ISSUER/.well-known/oauth-authorization-server"
        const val AUTHORIZATION_ENDPOINT = "$ISSUER/authorize"
        const val TOKEN_ENDPOINT = "$ISSUER/token"
        const val CREDENTIAL_ENDPOINT = "$ISSUER/credential"
        const val NONCE_ENDPOINT = "$ISSUER/nonce"
        const val DEFERRED_ENDPOINT = "$ISSUER/deferred"
        const val PAR_ENDPOINT = "$ISSUER/par"
        const val REDIRECT_URI = "wallet.example:/callback"
    }

    private class RecordingCredentialStore : WalletCredentialStore {
        val credentials = mutableListOf<StoredCredential>()

        override suspend fun getCredential(id: String): StoredCredential? = credentials.find { it.id == id }
        override suspend fun listCredentials(): Flow<StoredCredential> = flowOf(*credentials.toTypedArray())
        override suspend fun addCredential(entry: StoredCredential) { credentials += entry }
        override suspend fun removeCredential(id: String): Boolean = credentials.removeAll { it.id == id }
    }
}

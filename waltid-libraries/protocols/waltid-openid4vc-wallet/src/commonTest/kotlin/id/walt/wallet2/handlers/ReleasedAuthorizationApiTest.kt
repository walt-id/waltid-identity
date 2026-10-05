package id.walt.wallet2.handlers

import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import kotlin.test.*

class ReleasedAuthorizationApiTest {
    private val offer = JsonObject(batchTestOffer() + ("credential_configuration_ids" to
            JsonArray(listOf("identity", "other").map(::JsonPrimitive))))
    private val metadata = Json.parseToJsonElement(batchTestMetadata()).jsonObject.let { metadata ->
        val configs = metadata.getValue("credential_configurations_supported").jsonObject
        JsonObject(metadata + ("credential_configurations_supported" to JsonObject(configs +
                ("other" to JsonObject(configs.getValue("identity").jsonObject + ("scope" to JsonPrimitive("other"))))))).toString()
    }

    @Test
    fun releasedScopeSwitchAndFirstConfigurationApplyToRedirectAndPar() = runTest {
        val fixture = batchTestFixture(true)
        for (pushed in listOf(false, true)) for (useScope in listOf(false, true)) {
            var parRequests = 0
            fun checkParameters(parameters: Parameters) {
                if (useScope) {
                    assertEquals("identity", parameters["scope"])
                    assertNull(parameters["authorization_details"])
                } else {
                    assertNull(parameters["scope"])
                    val details = Json.parseToJsonElement(assertNotNull(parameters["authorization_details"])).jsonArray
                    assertEquals("identity", details.single().jsonObject["credential_configuration_id"]?.jsonPrimitive?.content)
                }
            }
            batchTestClient(metadata = metadata, par = if (pushed) ({ parameters ->
                parRequests++
                checkParameters(parameters)
            }) else null, credential = { error("Authorization must not issue credentials") }).use { http ->
                val result = WalletIssuanceHandler.generateAuthorizationUrl(fixture.wallet,
                    GenerateAuthorizationUrlRequest(offerJson = offer, useScope = useScope), httpClient = http)
                assertEquals("identity", result.credentialConfigurationId)
                if (pushed) {
                    assertEquals(setOf("client_id", "request_uri"), result.authorizationUrl.parameters.names())
                } else checkParameters(result.authorizationUrl.parameters)
                assertEquals(if (pushed) 1 else 0, parRequests)
                // Released single-result JSON has no unknown plural projection for strict clients.
                assertFalse("credentialConfigurationIds" in Json.encodeToJsonElement(result).jsonObject)
            }
        }
        batchTestClient(metadata = batchTestMetadata().replace("\"scope\":\"identity\",", ""),
            credential = { error("Authorization must not issue credentials") }).use { http ->
            assertFailsWith<IllegalArgumentException> {
                WalletIssuanceHandler.generateAuthorizationUrl(fixture.wallet,
                    GenerateAuthorizationUrlRequest(offerJson = batchTestOffer(), useScope = true), httpClient = http)
            }
        }
    }

    @Test
    fun batchAuthorizationPreservesAllTargetsAndNegotiatesForRedirectAndPar() = runTest {
        val fixture = batchTestFixture(true)
        for (pushed in listOf(false, true)) for (detailsSupported in listOf(false, true)) {
            fun checkParameters(parameters: Parameters) {
                if (detailsSupported) {
                    assertNull(parameters["scope"])
                    val details = Json.parseToJsonElement(assertNotNull(parameters["authorization_details"])).jsonArray
                    assertEquals(listOf("identity", "other"), details.map {
                        it.jsonObject.getValue("credential_configuration_id").jsonPrimitive.content
                    })
                } else {
                    assertNull(parameters["authorization_details"])
                    assertEquals("identity other", parameters["scope"])
                }
            }
            batchTestClient(metadata = metadata, authorizationDetailsSupported = detailsSupported,
                par = if (pushed) ::checkParameters else null,
                credential = { error("Authorization must not issue credentials") }).use { http ->
                val result = WalletIssuanceHandler.generateBatchAuthorizationUrl(fixture.wallet,
                    GenerateBatchAuthorizationUrlRequest(offerJson = offer), httpClient = http)
                assertEquals(listOf("identity", "other"), result.credentialConfigurationIds)
                if (!pushed) checkParameters(result.authorizationUrl.parameters)
                assertFalse("credentialConfigurationId" in Json.encodeToJsonElement(result).jsonObject)
            }
        }
    }

    @Test
    fun releasedDtoContractsAndBatchSelectionValidationRemainExplicit() {
        val request = GenerateAuthorizationUrlRequest(null, offer, "client", Url("openid://"), false, true)
        val copied = request.copy(clientId = "other")
        assertTrue(copied.component6())
        assertFalse(copied.component5())
        assertEquals(copied, Json.decodeFromString<GenerateAuthorizationUrlRequest>(Json.encodeToString(copied)))
        val result = GenerateAuthorizationUrlResult(Url("https://issuer.example/authorize"), "state", null,
            "identity", BATCH_TEST_ISSUER)
        assertEquals("identity", result.copy(state = "other").component4())
        for (ids in listOf(emptyList(), listOf(""), listOf("identity", "identity"))) {
            assertFailsWith<IllegalArgumentException> {
                GenerateBatchAuthorizationUrlRequest(offerJson = offer, credentialConfigurationIds = ids)
            }
        }
    }

    @Test
    fun authorizedRequestContractsRejectInvalidEndpointsBeforeRedeemingTheCode() = runTest {
        val fixture = batchTestFixture(true)
        val legacy = ReceiveAuthorizedCredentialRequest("code", null, BATCH_TEST_ISSUER,
            Url("$BATCH_TEST_ISSUER/credential"), "identity", nonceEndpoint = Url("$BATCH_TEST_ISSUER/nonce"))
        val configuration: String = legacy.copy(code = "next").component5()
        assertEquals("identity", configuration)
        assertEquals(legacy, Json.decodeFromString<ReceiveAuthorizedCredentialRequest>(Json.encodeToString(legacy)))
        val batch = ReceiveAuthorizedCredentialsRequest(listOf(fixture.selection(2)), "code",
            credentialIssuer = legacy.credentialIssuer, credentialEndpoint = legacy.credentialEndpoint,
            nonceEndpoint = legacy.nonceEndpoint)
        assertFailsWith<IllegalArgumentException> { batch.copy(credentials = emptyList()) }
        var redemptions = 0
        batchTestClient(token = { redemptions++; BATCH_TEST_TOKEN }, credential = { error("Must not issue") }).use { http ->
            for (invalidNonce in listOf(false, true)) {
                val endpoint = if (invalidNonce) legacy.credentialEndpoint else Url("https://wrong.example/credential")
                val nonce = if (invalidNonce) Url("https://wrong.example/nonce") else legacy.nonceEndpoint
                assertFailsWith<IllegalArgumentException> {
                    WalletIssuanceHandler.receiveCredentialAuthCode(fixture.wallet,
                        legacy.copy(credentialEndpoint = endpoint, nonceEndpoint = nonce), httpClient = http)
                }
                assertFailsWith<IllegalArgumentException> {
                    WalletIssuanceHandler.receiveCredentialsAuthCode(fixture.wallet,
                        batch.copy(credentialEndpoint = endpoint, nonceEndpoint = nonce), httpClient = http)
                }
                assertFailsWith<IllegalArgumentException> {
                    WalletIssuanceHandler.receiveCredentialAuthCodeFlow(fixture.wallet, "code", null,
                        BATCH_TEST_ISSUER, endpoint, "identity", nonceEndpoint = nonce?.toString(), httpClient = http).toList()
                }
            }
            assertEquals(0, redemptions)
        }
    }


    @Test
    fun releasedDeferredCallbacksRetainConfigurationAndTransactionForBothOfferEntries() = runTest {
        for (reviewed in listOf(false, true)) {
            val fixture = batchTestFixture(true)
            val deferred = mutableListOf<Pair<String, String>>()
            batchTestClient(credentialStatus = io.ktor.http.HttpStatusCode.Accepted,
                credential = { """{"transaction_id":"transaction","interval":5}""" }).use { http ->
                val flow = if (reviewed) {
                    val preview = WalletIssuanceHandler.previewOffer(fixture.wallet,
                        ResolveOfferRequest(offerJson = batchTestOffer()), httpClient = http)
                    WalletIssuanceHandler.receiveCredentialFlow(fixture.wallet,
                        ReceiveCredentialFromPreviewRequest(preview.previewHandle), httpClient = http,
                        onDeferredTransactionId = { configuration, transaction -> deferred += configuration to transaction })
                } else WalletIssuanceHandler.receiveCredentialFlow(fixture.wallet,
                    ReceiveCredentialRequest(offerJson = batchTestOffer()), httpClient = http,
                    onDeferredTransactionId = { configuration, transaction -> deferred += configuration to transaction })
                assertTrue(flow.toList().isEmpty())
                assertEquals(listOf("identity" to "transaction"), deferred)
            }
        }
    }

}

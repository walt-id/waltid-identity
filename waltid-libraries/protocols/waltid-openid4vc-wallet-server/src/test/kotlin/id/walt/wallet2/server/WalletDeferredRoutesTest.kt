package id.walt.wallet2.server

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.*
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.test.*

class WalletDeferredRoutesTest {
    @Test
    fun deferredRoutesEnforceOwnershipAndReturnOnlyPublicContinuationData() = exerciseDeferredRoutes(false)

    @Test
    fun walletDeletionAfterALostResponseRemovesTheContinuationWithoutRepolling() = exerciseDeferredRoutes(true)

    private fun exerciseDeferredRoutes(loseResponse: Boolean) = testApplication {
        val keys = InMemoryKeyStore()
        val keyId = keys.addKey(JWKKey.generate(KeyType.secp256r1))
        val records = InMemoryIssuanceSessionStore()
        val wallet = Wallet("owner-wallet", keyStores = listOf(keys),
            credentialStores = listOf(InMemoryCredentialStore()), defaultKeyId = keyId).attachIssuanceSessionState(WalletIssuanceSessionState("owner-wallet", records))
        var polls = 0
        val issuerClient = HttpClient(MockEngine) {
            engine { addHandler { request ->
                val content = when (request.url.encodedPath) {
                    "/.well-known/openid-credential-issuer" -> """{
                      "credential_issuer":"https://issuer.example","credential_endpoint":"https://issuer.example/credential",
                      "deferred_credential_endpoint":"https://issuer.example/deferred",
                      "credential_configurations_supported":{"identity":{"format":"dc+sd-jwt","vct":"identity","scope":"identity","credential_metadata":{"display":[{"name":"Identity card"}]}}}}
                    """
                    "/.well-known/oauth-authorization-server" -> """{
                      "issuer":"https://issuer.example","token_endpoint":"https://issuer.example/token","authorization_endpoint":"https://issuer.example/authorize","response_types_supported":["code"]}
                    """
                    "/token" -> """{"access_token":"private-access-token","token_type":"Bearer"}"""
                    "/credential" -> """{"transaction_id":"private-transaction","interval":1}"""
                    "/deferred" -> {
                        polls++
                        if (loseResponse) error("Response lost after sending")
                        assertEquals("Bearer private-access-token", request.headers[HttpHeaders.Authorization])
                        """{"transaction_id":"private-transaction","interval":7}"""
                    }
                    else -> error("Unexpected endpoint ${request.url}")
                }
                respond(content, if (request.url.encodedPath in listOf("/credential", "/deferred"))
                    HttpStatusCode.Accepted else HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            } }
            install(ClientContentNegotiation) { json() }
        }
        val sessions = wallet.issuanceSessions(issuerClient)
        val review = sessions.start(WalletIssuanceSessionRequest(offerJson = Json.parseToJsonElement("""{
          "credential_issuer":"https://issuer.example","credential_configuration_ids":["identity"],
          "grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"private-code"}}}
        """).jsonObject))
        val pending = assertIs<WalletIssuanceOutcome.Deferred>(sessions.continuePreAuthorized(review.id)).credentials.single()
        val wallets = InMemoryWalletStore()
        wallets.saveWallet(wallet)
        wallets.saveWallet(Wallet("other-wallet"))
        wallets.linkWalletToAccount("owner", wallet.id)
        wallets.linkWalletToAccount("other", "other-wallet")
        val resolver = object : WalletResolver {
            override val publicBaseUrl = Url("https://wallet.example")
            override val walletStore = wallets
        }
        application {
            install(ContentNegotiation) { json() }
            routing { registerWallet2Routes(resolver, getAccountId = { request.headers["X-Test-Account"] }) }
        }
        val path = "/wallet/${wallet.id}/credentials/receive/deferred"
        assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("$path/${pending.id}").status)
        assertEquals(HttpStatusCode.Forbidden, client.get(path) { header("X-Test-Account", "other") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("$path?includeDetails=true") { header("X-Test-Account", "other") }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("$path/${pending.id}") { header("X-Test-Account", "other") }.status)
        assertEquals(0, polls)
        val foreign = client.post("/wallet/other-wallet/credentials/receive/deferred/${pending.id}") {
            header("X-Test-Account", "other")
        }
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(WalletIssuanceErrorCode.INVALID_SESSION,
            assertIs<WalletIssuanceOutcome.Failed>(json.decodeFromString<WalletIssuanceOutcome>(foreign.bodyAsText())).error.code)
        assertEquals(0, polls)
        val listed = client.get(path) { header("X-Test-Account", "owner") }
        assertEquals(HttpStatusCode.OK, listed.status)
        assertEquals(listOf(WalletIssuanceContinuation(pending)), json.decodeFromString<List<WalletIssuanceContinuation>>(listed.bodyAsText()))
        // Released clients decode strictly: presentation fields must remain opt-in.
        assertEquals(listOf(pending), Json.decodeFromString<List<WalletDeferredCredential>>(listed.bodyAsText()))
        val detailed = client.get("$path?includeDetails=true") { header("X-Test-Account", "owner") }
        assertEquals(HttpStatusCode.OK, detailed.status)
        val continuation = Json.decodeFromString<List<WalletIssuanceContinuation>>(detailed.bodyAsText()).single()
        assertEquals(pending.id, continuation.id)
        assertEquals(WalletIssuanceContinuationStatus.AWAITING_ISSUER, continuation.status)
        assertContains(assertNotNull(continuation.displayMetadataJson), "Identity card")
        withContext(Dispatchers.Default) { delay(1000) }
        val resumed = client.post("$path/${pending.id}") { header("X-Test-Account", "owner") }
        if (loseResponse) {
            val outcome = assertIs<WalletIssuanceOutcome.Failed>(json.decodeFromString<WalletIssuanceOutcome>(resumed.bodyAsText()))
            assertEquals(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN, outcome.error.code)
            assertFailsWith<IllegalStateException> { sessions.clearSessions() }
            // Re-open the wallet with a fresh runtime, preserving the durable uncertain claim.
            wallets.saveWallet(wallet.copy().attachIssuanceSessionState(WalletIssuanceSessionState(wallet.id, records)))
            val deleted = client.delete("/wallet/${wallet.id}") { header("X-Test-Account", "owner") }
            assertEquals(HttpStatusCode.NoContent, deleted.status, deleted.bodyAsText())
            assertNull(resolver.resolveWallet(wallet.id))
            assertTrue(records.list().isEmpty())
            assertTrue(sessions.listDeferredCredentials().isEmpty())
        } else {
            assertEquals(HttpStatusCode.OK, resumed.status)
            val outcome = assertIs<WalletIssuanceOutcome.Deferred>(json.decodeFromString<WalletIssuanceOutcome>(resumed.bodyAsText()))
            assertEquals(pending.copy(intervalSeconds = 7), outcome.credentials.single())
            val early = client.post("$path/${pending.id}") { header("X-Test-Account", "owner") }
            assertIs<WalletIssuanceOutcome.Deferred>(json.decodeFromString<WalletIssuanceOutcome>(early.bodyAsText()))
        }
        assertEquals(1, polls)
        for (body in listOf(listed.bodyAsText(), detailed.bodyAsText(), resumed.bodyAsText())) {
            for (secret in listOf("private-access-token", "private-transaction", "private-code", "keyId")) {
                assertFalse(secret in body)
            }
        }
        issuerClient.close()
    }
}

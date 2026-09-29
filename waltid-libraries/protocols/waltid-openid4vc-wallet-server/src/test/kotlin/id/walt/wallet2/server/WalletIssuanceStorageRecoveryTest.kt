package id.walt.wallet2.server

import com.sun.net.httpserver.HttpServer
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.handlers.*
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.inmemory.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class WalletIssuanceStorageRecoveryTest {
    private enum class RequestKind { FETCH, POLL, PRE_AUTHORIZED, AUTHORIZED }
    @Test
    fun publicFetchReturnsPartialStorageAndResumesWithoutRefetching() = exerciseStorageRecovery(RequestKind.FETCH)

    @Test
    fun legacyPublicPollReturnsStorageRecoveryWithoutInventingAConfiguration() = exerciseStorageRecovery(RequestKind.POLL)

    @Test
    fun publicPreAuthorizedFlowRetainsTheBatchWhenNoWriteSucceeds() = exerciseStorageRecovery(RequestKind.PRE_AUTHORIZED)

    @Test
    fun publicAuthorizedFlowRetainsTheBatchWhenNoWriteSucceeds() = exerciseStorageRecovery(RequestKind.AUTHORIZED)

    private fun exerciseStorageRecovery(kind: RequestKind) = testApplication {
        val poll = kind == RequestKind.POLL
        val full = kind == RequestKind.PRE_AUTHORIZED || kind == RequestKind.AUTHORIZED
        val issuer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val issuerUrl = "http://127.0.0.1:${issuer.address.port}"
        val key = JWKKey.generate(KeyType.secp256r1)
        val publicJwk = key.getPublicKey().exportJWKObject()
        val credential = key.signJws(buildJsonObject {
            put("iss", issuerUrl)
            put("vct", "identity")
            putJsonObject("cnf") { put("jwk", publicJwk) }
        }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("dc+sd-jwt"))) + "~"
        val fetches = AtomicInteger()
        issuer.createContext("/") { exchange ->
            val response = when (exchange.requestURI.path) {
                "/.well-known/openid-credential-issuer" -> """{
                  "credential_issuer":"$issuerUrl","credential_endpoint":"$issuerUrl/credential",
                  "batch_credential_issuance":{"batch_size":2},
                  "credential_configurations_supported":{"identity":{"format":"dc+sd-jwt","vct":"identity","scope":"identity",
                    "cryptographic_binding_methods_supported":["jwk"],
                    "proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}}}}}
                """
                "/.well-known/oauth-authorization-server" -> """{
                  "issuer":"$issuerUrl","token_endpoint":"$issuerUrl/token","authorization_endpoint":"$issuerUrl/authorize",
                  "response_types_supported":["code"],"authorization_details_types_supported":["openid_credential"]}"""
                "/token" -> {
                    exchange.requestBody.use { it.readBytes() }
                    """{"access_token":"test-token","token_type":"Bearer"}"""
                }
                "/credential", "/deferred" -> {
                    fetches.incrementAndGet()
                    exchange.requestBody.use { it.readBytes() }
                    """{"credentials":[{"credential":"$credential"},{"credential":"$credential"}]}"""
                }
                else -> error("Unexpected issuer request ${exchange.requestURI.path}")
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        issuer.start()
        try {
            val keys = InMemoryKeyStore()
            val keyId = keys.addKey(key)
            val saved = InMemoryCredentialStore()
            var writes = 0
            val failingStore = object : WalletCredentialStore by saved {
                override suspend fun addCredential(entry: StoredCredential) {
                    check(++writes != if (full) 1 else 2) { "Credential write failed" }
                    saved.addCredential(entry)
                }
            }
            val wallet = Wallet("isolated-wallet", keyStores = listOf(keys), credentialStores = listOf(failingStore), defaultKeyId = keyId)
                .attachIssuanceSessionState(WalletIssuanceSessionState("isolated-wallet", InMemoryIssuanceSessionStore()))
            val wallets = InMemoryWalletStore().also { it.saveWallet(wallet) }
            val resolver = object : WalletResolver {
                override val publicBaseUrl = Url("https://wallet.example")
                override val walletStore = wallets
            }
            application {
                install(ContentNegotiation) { json() }
                routing { registerWallet2Routes(resolver) }
            }
            val path = "/wallet/${wallet.id}/credentials/receive"
            val fetched = client.post(when (kind) {
                RequestKind.FETCH -> "$path/fetch-credential"
                RequestKind.POLL -> "$path/deferred"
                RequestKind.PRE_AUTHORIZED -> path
                RequestKind.AUTHORIZED -> "$path/authorized"
            }) {
                contentType(ContentType.Application.Json)
                val selections = """[{"credentialConfigurationId":"identity","holderBindings":[{"keyId":"$keyId"},{"keyId":"$keyId"}]}]"""
                setBody(when (kind) {
                    RequestKind.FETCH -> """{"credentialEndpoint":"$issuerUrl/credential","accessToken":"test-token",
                      "credentialConfigurationId":"identity","credentialIdentifier":"dataset-a","storeInWallet":true,
                      "credentialIssuerBaseUrl":"$issuerUrl","proofs":{"jwt":["proof-one","proof-two"]},
                      "holderBindings":[{"keyId":"$keyId"},{"keyId":"$keyId"}]}"""
                    RequestKind.POLL -> """{"deferredCredentialEndpoint":"$issuerUrl/deferred","accessToken":"test-token",
                      "transactionId":"transaction","proofRequired":true,
                      "holderBindings":[{"keyId":"$keyId"},{"keyId":"$keyId"}]}"""
                    RequestKind.PRE_AUTHORIZED -> """{"credentials":$selections,"offerJson":{
                      "credential_issuer":"$issuerUrl","credential_configuration_ids":["identity"],
                      "grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}}}"""
                    RequestKind.AUTHORIZED -> """{"credentials":$selections,"code":"code",
                      "credentialIssuer":"$issuerUrl","credentialEndpoint":"$issuerUrl/credential"}"""
                })
            }
            assertEquals(HttpStatusCode.MultiStatus, fetched.status, fetched.bodyAsText())
            val failed = if (full) {
                val result = Json.decodeFromString<ReceiveCredentialResult>(fetched.bodyAsText())
                assertTrue(result.credentialIds.isEmpty())
                assertTrue(result.deferredCredentials.isEmpty())
                assertEquals(CredentialIssuanceStage.STORAGE, assertNotNull(result.failure).stage)
                assertNotNull(result.storageOutcome)
            } else if (poll) {
                val result = Json.decodeFromString<PollDeferredResult>(fetched.bodyAsText())
                assertNull(result.pending)
                assertEquals(1, result.credentialIds.size)
                assertIs<WalletIssuanceOutcome.Failed>(result.storageOutcome)
            } else {
                val result = Json.decodeFromString<FetchCredentialResult>(fetched.bodyAsText())
                assertEquals(2, result.rawCredentials.size)
                assertIs<WalletIssuanceOutcome.Failed>(result.storageOutcome)
            }
            assertEquals(WalletIssuanceErrorCode.STORAGE, failed.error.code)
            assertEquals(if (full) 0 else 1, failed.storedCredentialIds.size)
            val handle = failed.deferredCredentials.single()
            assertEquals(if (kind == RequestKind.FETCH) "dataset-a" else null, handle.credentialIdentifier)
            assertEquals(if (poll) null else "identity", handle.credentialConfigurationId)
            val listed = client.get("$path/deferred")
            assertEquals(listOf(handle), Json.decodeFromString<List<WalletDeferredCredential>>(listed.bodyAsText()))
            val resumed = client.post("$path/deferred/${handle.id}")
            assertEquals(HttpStatusCode.OK, resumed.status, resumed.bodyAsText())
            val stored = assertIs<WalletIssuanceOutcome.Stored>(Json.decodeFromString<WalletIssuanceOutcome>(resumed.bodyAsText()))
            assertEquals(2, stored.credentialIds.size)
            assertTrue(stored.credentialIds.containsAll(failed.storedCredentialIds))
            assertEquals(1, fetches.get())
            assertEquals("[]", client.get("$path/deferred").bodyAsText())
            assertFalse("test-token" in fetched.bodyAsText())
        } finally { issuer.stop(0) }
    }
}

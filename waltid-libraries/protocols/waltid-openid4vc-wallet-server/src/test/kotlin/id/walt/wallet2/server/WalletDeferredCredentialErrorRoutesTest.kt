package id.walt.wallet2.server

import com.sun.net.httpserver.HttpServer
import id.walt.openid4vci.errors.CredentialError
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.handlers.CredentialEndpointException
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class WalletDeferredCredentialErrorRoutesTest {
    @Test
    fun deferredPollPreservesIssuerErrorsWithoutRetryingOrStorage() = testApplication {
        val upstream = AtomicReference(400 to "{}")
        val polls = AtomicInteger()
        val writes = AtomicInteger()
        val issuer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        issuer.createContext("/deferred") { exchange ->
            polls.incrementAndGet()
            exchange.requestBody.use { it.readBytes() }
            val (status, body) = upstream.get()
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        issuer.start()
        try {
            val stored = InMemoryCredentialStore()
            val wallet = Wallet("deferred-errors", credentialStores = listOf(object : WalletCredentialStore by stored {
                override suspend fun addCredential(entry: StoredCredential) {
                    writes.incrementAndGet()
                    stored.addCredential(entry)
                }
            }))
            val wallets = InMemoryWalletStore()
            wallets.saveWallet(wallet)
            wallets.linkWalletToAccount("owner", wallet.id)
            val resolver = object : WalletResolver {
                override val publicBaseUrl = Url("https://wallet.example")
                override val walletStore = wallets
            }
            application {
                install(ContentNegotiation) { json() }
                install(StatusPages) {
                    exception<CredentialEndpointException> { call, _ ->
                        call.respond(HttpStatusCode.InternalServerError, "Unhandled credential endpoint error")
                    }
                }
                routing { registerWallet2Routes(resolver, getAccountId = { request.headers["X-Test-Account"] }) }
            }
            suspend fun poll(detailed: Boolean, account: String? = "owner") =
                client.post("/wallet/${wallet.id}/credentials/receive/deferred") {
                    account?.let { header("X-Test-Account", it) }
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("deferredCredentialEndpoint", "http://127.0.0.1:${issuer.address.port}/deferred")
                        put("transactionId", "private-transaction")
                        put("accessToken", "private-access-token")
                        if (detailed) put("credentialIdentifier", "identity-dataset")
                    }.toString())
                }

            assertEquals(HttpStatusCode.Unauthorized, poll(false, account = null).status)
            assertEquals(HttpStatusCode.Forbidden, poll(false, account = "other").status)
            assertEquals(0, polls.get(), "Wallet authorization must precede the issuer poll")

            val structured = listOf(
                400 to "credential_request_denied",
                400 to "invalid_transaction_id",
                400 to "invalid_nonce",
                401 to "invalid_token",
                401 to "invalid_proof",
                403 to "credential_request_denied",
                429 to "issuer_extension_error",
            )
            for (detailed in listOf(false, true)) for ((status, code) in structured)
                for (description in listOf(null, "Issuer rejected $code")) {
                    val expected = CredentialError(code, description)
                    upstream.set(status to Json.encodeToString(expected))
                    val before = polls.get()
                    val response = poll(detailed)
                    val body = response.bodyAsText()
                    assertEquals(HttpStatusCode.fromValue(status), response.status,
                        "$status / $code (detailed=$detailed): $body")
                    assertEquals(expected, Json.decodeFromString<CredentialError>(body))
                    assertEquals(before + 1, polls.get(), "Deferred refusals must not trigger a retry")
                    assertEquals(0, writes.get())
                    assertTrue(stored.listCredentials().toList().isEmpty())
                }

            // Malformed issuer errors and all issuer 5xx still reach the existing exception handler.
            for (detailed in listOf(false, true)) for ((status, body) in listOf(
                400 to "not-json",
                400 to "{}",
                401 to "not-json",
                403 to "{}",
                500 to """{"error":"invalid_nonce"}""",
            )) {
                upstream.set(status to body)
                val before = polls.get()
                val response = poll(detailed)
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertEquals("Unhandled credential endpoint error", response.bodyAsText())
                assertEquals(before + 1, polls.get(), "Fallback errors must not trigger a retry")
                assertEquals(0, writes.get())
                assertTrue(stored.listCredentials().toList().isEmpty())
            }
        } finally {
            issuer.stop(0)
        }
    }
}

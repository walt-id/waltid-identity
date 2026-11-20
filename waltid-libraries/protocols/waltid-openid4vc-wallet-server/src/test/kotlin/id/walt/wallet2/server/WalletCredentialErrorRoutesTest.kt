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
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class WalletCredentialErrorRoutesTest {
    @Test
    fun isolatedFetchPreservesStructuredIssuerClientErrorsWithoutRetryingOrStorage() = testApplication {
        val upstream = AtomicReference(400 to "{}")
        val credentialRequests = AtomicInteger()
        val writes = AtomicInteger()
        val issuer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        issuer.createContext("/credential") { exchange ->
            credentialRequests.incrementAndGet()
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
            val wallet = Wallet("credential-errors", credentialStores = listOf(object : WalletCredentialStore by stored {
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
                routing { registerWallet2Routes(resolver, getAccountId = { "owner" }) }
            }
            suspend fun fetch(storeInWallet: Boolean) = client.post("/wallet/${wallet.id}/credentials/receive/fetch-credential") {
                contentType(ContentType.Application.Json)
                setBody("""{"credentialEndpoint":"http://127.0.0.1:${issuer.address.port}/credential",
                    "accessToken":"private-access-token","credentialConfigurationId":"identity","storeInWallet":$storeInWallet}""")
            }

            val codes = listOf("invalid_proof", "invalid_credential_request", "invalid_encryption_parameters",
                "invalid_nonce", "unknown_credential_configuration", "unknown_credential_identifier",
                "credential_request_denied", "issuer_extension_error")
            val structured = codes.map { 400 to it } + listOf(
                401 to "invalid_token",
                // WAL-1657's former 401 -> 500 case now deliberately follows the structured 4xx policy.
                401 to "invalid_proof",
                403 to "credential_request_denied",
                429 to "issuer_extension_error",
            )
            for (store in listOf(false, true)) for ((status, code) in structured)
                for (description in listOf(null, "Issuer rejected $code")) {
                    val expected = CredentialError(code, description)
                    upstream.set(status to Json.encodeToString(expected))
                    val before = credentialRequests.get()
                    val response = fetch(store)
                    assertEquals(HttpStatusCode.fromValue(status), response.status,
                        "$status / $code (store=$store): ${response.bodyAsText()}")
                    assertEquals(expected, Json.decodeFromString<CredentialError>(response.bodyAsText()))
                    assertEquals(before + 1, credentialRequests.get(), "Isolated fetch must not retry issuer errors")
                    assertEquals(0, writes.get())
                    assertTrue(stored.listCredentials().toList().isEmpty())
                }

            // Invalid bodies and all upstream 5xx retain the existing exception path, even for invalid_nonce.
            for (store in listOf(false, true)) for ((status, body) in listOf(
                400 to "not-json",
                400 to "{}",
                401 to "not-json",
                401 to "{}",
                403 to "not-json",
                403 to "{}",
                500 to """{"error":"invalid_nonce"}""",
                502 to """{"error":"server_error"}""",
                503 to """{"error":"temporarily_unavailable"}""",
            )) {
                upstream.set(status to body)
                val before = credentialRequests.get()
                val response = fetch(store)
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertEquals("Unhandled credential endpoint error", response.bodyAsText())
                assertEquals(before + 1, credentialRequests.get(), "Fallback errors must not trigger a retry")
                assertEquals(0, writes.get())
                assertTrue(stored.listCredentials().toList().isEmpty())
            }
        } finally {
            issuer.stop(0)
        }
    }
}

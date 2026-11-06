package id.walt.wallet2.server

import com.sun.net.httpserver.HttpServer
import id.walt.openid4vci.errors.CredentialError
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.CredentialEndpointException
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
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
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class WalletCredentialErrorRoutesTest {
    @Test
    fun isolatedFetchPreservesStructuredIssuerBadRequests() = testApplication {
        val upstream = AtomicReference(400 to "{}")
        val credentialRequests = AtomicInteger()
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
            val wallet = Wallet("credential-errors")
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
            for (store in listOf(false, true)) for (code in codes) for (description in listOf(null, "Issuer rejected $code")) {
                val expected = CredentialError(code, description)
                upstream.set(400 to Json.encodeToString(expected))
                val before = credentialRequests.get()
                val response = fetch(store)
                assertEquals(HttpStatusCode.BadRequest, response.status, "$code (store=$store): ${response.bodyAsText()}")
                assertEquals(expected, Json.decodeFromString<CredentialError>(response.bodyAsText()))
                assertEquals(before + 1, credentialRequests.get(), "Isolated fetch must not retry issuer errors")
            }

            // Only structured HTTP 400 responses belong to the CredentialError contract.
            for ((status, body) in listOf(
                400 to "not-json",
                400 to "{}",
                401 to """{"error":"invalid_proof"}""",
                500 to """{"error":"invalid_nonce"}""",
            )) {
                upstream.set(status to body)
                val response = fetch(false)
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertEquals("Unhandled credential endpoint error", response.bodyAsText())
            }
        } finally {
            issuer.stop(0)
        }
    }
}

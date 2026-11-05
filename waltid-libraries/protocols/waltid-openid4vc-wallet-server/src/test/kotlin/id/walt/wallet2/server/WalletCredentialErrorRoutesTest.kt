package id.walt.wallet2.server

import com.sun.net.httpserver.HttpServer
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
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

class WalletCredentialErrorRoutesTest {
    @Test fun isolatedFetchPreservesStructuredIssuerClientErrorsWithoutRetrying() = testApplication {
        val issuer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        issuer.createContext("/") { exchange ->
            calls.incrementAndGet()
            exchange.requestBody.use { it.readBytes() }
            val code = exchange.requestURI.path.removePrefix("/")
            val status = if (code == "invalid_token") 401 else 400
            val body = """{"error":"$code","error_description":"Issuer rejected the request"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        issuer.start()
        try {
            val wallets = InMemoryWalletStore().also { it.saveWallet(Wallet("wallet")) }
            val resolver = object : WalletResolver {
                override val publicBaseUrl = Url("https://wallet.example")
                override val walletStore = wallets
            }
            application {
                install(ContentNegotiation) { json() }
                routing { registerWallet2Routes(resolver) }
            }
            for (code in listOf("invalid_proof", "invalid_nonce", "invalid_token")) {
                val response = client.post("/wallet/wallet/credentials/receive/fetch-credential") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"credentialEndpoint":"http://127.0.0.1:${issuer.address.port}/$code",
                        "accessToken":"token","credentialConfigurationId":"identity","proofJwt":"rejected-proof"}""")
                }
                assertEquals(if (code == "invalid_token") HttpStatusCode.Unauthorized else HttpStatusCode.BadRequest,
                    response.status, response.bodyAsText())
                val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                assertEquals(code, error.getValue("error").jsonPrimitive.content)
                assertEquals("Issuer rejected the request", error.getValue("error_description").jsonPrimitive.content)
            }
            assertEquals(3, calls.get())
        } finally { issuer.stop(0) }
    }
}

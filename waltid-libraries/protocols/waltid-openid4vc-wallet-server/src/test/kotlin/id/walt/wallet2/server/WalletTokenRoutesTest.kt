package id.walt.wallet2.server

import com.sun.net.httpserver.HttpServer
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.server.models.ResolveBatchOfferResponse
import id.walt.wallet2.server.models.ResolveOfferDetailedResponse
import id.walt.wallet2.handlers.RequestTokenDetailedResult
import id.walt.wallet2.handlers.RequestTokenResult
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class WalletTokenRoutesTest {
    @Test
    fun isolatedRoutesPreserveReleasedWireAndExposeBatchMetadata() = testApplication {
        val issuer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val issuerUrl = "http://127.0.0.1:${issuer.address.port}"
        val tokenCalls = AtomicInteger()
        issuer.createContext("/") { exchange ->
            val response = when (exchange.requestURI.path) {
                "/.well-known/openid-credential-issuer" -> """{
                  "credential_issuer":"$issuerUrl","credential_endpoint":"$issuerUrl/credential","batch_credential_issuance":{"batch_size":2},
                  "credential_configurations_supported":{"identity":{"format":"dc+sd-jwt","vct":"identity","scope":"identity"}}}"""
                "/.well-known/oauth-authorization-server" -> """{
                  "issuer":"$issuerUrl","token_endpoint":"$issuerUrl/token","authorization_endpoint":"$issuerUrl/authorize",
                  "response_types_supported":["code"]}"""
                "/token" -> {
                    exchange.requestBody.use { it.readBytes() }
                    tokenCalls.incrementAndGet()
                    """{"access_token":"access","expires_in":60,"token_type":"Bearer","scope":"identity",
                      "authorization_details":[{"type":"openid_credential","credential_configuration_id":"identity",
                      "credential_identifiers":["dataset-a","dataset-b"]}]}"""
                }
                else -> error("Unexpected issuer request ${exchange.requestURI.path}")
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        issuer.start()
        try {
            val wallet = Wallet("token-wallet")
            val wallets = InMemoryWalletStore()
            wallets.saveWallet(wallet)
            wallets.linkWalletToAccount("owner", wallet.id)
            val resolver = object : WalletResolver {
                override val publicBaseUrl = Url("https://wallet.example")
                override val walletStore = wallets
            }
            application {
                install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
                routing { registerWallet2Routes(resolver, getAccountId = { request.headers["X-Test-Account"] }) }
            }
            for (authCode in listOf(false, true)) for (batch in listOf(false, true)) {
                val route = if (authCode) "exchange-code" else "request-token"
                val path = "/wallet/${wallet.id}/credentials/receive/$route" + if (batch) "/batch" else ""
                val body = if (authCode) """{"code":"code","credentialIssuerBaseUrl":"$issuerUrl"}"""
                    else """{"tokenEndpoint":"$issuerUrl/token","preAuthorizedCode":"code","credentialIssuer":"$issuerUrl"}"""
                val before = tokenCalls.get()
                for (account in listOf(null, "other")) {
                    val denied = client.post(path) {
                        account?.let { header("X-Test-Account", it) }
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                    assertEquals(if (account == null) HttpStatusCode.Unauthorized else HttpStatusCode.Forbidden, denied.status)
                }
                assertEquals(before, tokenCalls.get())
                val response = client.post(path) {
                    header("X-Test-Account", "owner")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                if (batch) {
                    val result = Json.decodeFromString<RequestTokenDetailedResult>(response.bodyAsText())
                    assertEquals("access", result.accessToken)
                    assertEquals("identity", result.scope)
                    assertEquals(listOf("dataset-a", "dataset-b"), result.authorizationDetails?.single()?.credentialIdentifiers)
                } else {
                    assertEquals(RequestTokenResult("access", 60, "Bearer"), Json.decodeFromString<RequestTokenResult>(response.bodyAsText()))
                    assertEquals(setOf("accessToken", "expiresIn", "tokenType"), Json.parseToJsonElement(response.bodyAsText()).jsonObject.keys)
                }
                assertEquals(before + 1, tokenCalls.get())
            }
            for (batch in listOf(false, true)) {
                val path = "/wallet/${wallet.id}/credentials/receive/resolve-offer" + if (batch) "/batch" else ""
                val response = client.post(path) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"offerJson":{"credential_issuer":"$issuerUrl","credential_configuration_ids":["identity"],
                      "grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"code"}}}}""")
                }
                assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                val offer = if (batch) {
                    val result = Json.decodeFromString<ResolveBatchOfferResponse>(response.bodyAsText())
                    assertEquals(2, result.batchSize)
                    result.offer
                } else Json.decodeFromString<ResolveOfferDetailedResponse>(response.bodyAsText())
                assertEquals(issuerUrl, offer.credentialIssuer)
                assertEquals(listOf("identity"), offer.credentialConfigurationIds)
                assertEquals("code", offer.preAuthorizedCode)
                assertEquals(4, tokenCalls.get(), "Offer resolution must not redeem a grant")
            }
        } finally {
            issuer.stop(0)
        }
    }
}

package id.walt.wallet2.server

import com.sun.net.httpserver.HttpServer
import id.walt.errors.HttpStatusError
import id.walt.wallet2.data.Wallet
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
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletManualAttestationRoutesTest {
    @Test
    fun routesAcceptManualPairsAndRejectMalformedPairsBeforeTokenRequests() = testApplication {
        val tokenCalls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val issuer = "http://127.0.0.1:${server.address.port}"
        server.createContext("/") { exchange ->
            val body = when (exchange.requestURI.path) {
                "/.well-known/openid-credential-issuer" -> """{"credential_issuer":"$issuer",
                    "credential_endpoint":"$issuer/credential","credential_configurations_supported":{
                    "identity":{"format":"jwt_vc_json","scope":"identity"}}}"""
                "/.well-known/oauth-authorization-server" -> """{"issuer":"$issuer","token_endpoint":"$issuer/token",
                    "authorization_endpoint":"$issuer/authorize","response_types_supported":["code"],
                    "client_attestation_signing_alg_values_supported":["ES256"],"client_attestation_pop_signing_alg_values_supported":["ES256"],
                    "token_endpoint_auth_methods_supported":["attest_jwt_client_auth"]}"""
                "/token" -> {
                    tokenCalls.incrementAndGet()
                    assertEquals(listOf("manual-wia"), exchange.requestHeaders["OAuth-Client-Attestation"])
                    assertEquals(listOf("manual-pop"), exchange.requestHeaders["OAuth-Client-Attestation-PoP"])
                    exchange.requestBody.use { it.readBytes() }
                    """{"access_token":"access","token_type":"Bearer"}"""
                }
                else -> error("Unexpected issuer request ${exchange.requestURI}")
            }.toByteArray()
            exchange.responseHeaders.add(HttpHeaders.ContentType, "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val wallet = Wallet("manual")
            val wallets = InMemoryWalletStore()
            wallets.saveWallet(wallet)
            val resolver = object : WalletResolver {
                override val publicBaseUrl = Url("https://wallet.example")
                override val walletStore = wallets
            }
            application {
                install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
                install(StatusPages) {
                    exception<Throwable> { call, error ->
                        val status = when (error) {
                            is HttpStatusError -> HttpStatusCode.fromValue(error.status)
                            is IllegalArgumentException -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(status, error.message.orEmpty())
                    }
                }
                routing { registerWallet2Routes(resolver) }
            }
            val valid = mapOf("oauth-client-attestation" to "manual-wia", "OAUTH-CLIENT-ATTESTATION-POP" to "manual-pop")
            val malformed = listOf(
                mapOf("OAuth-Client-Attestation" to "manual-wia"),
                mapOf("OAuth-Client-Attestation-PoP" to "manual-pop"),
                valid + ("oauth-client-attestation" to ""),
                valid + ("OAUTH-CLIENT-ATTESTATION-POP" to " \t"),
                valid + ("OAuth-Client-Attestation" to "duplicate"),
                valid + ("OAuth-Client-Attestation-PoP" to "duplicate"),
            )
            for (authorized in listOf(false, true)) for (batch in listOf(false, true)) {
                val route = if (authorized) "exchange-code" else "request-token"
                val path = "/wallet/manual/credentials/receive/$route" + if (batch) "/batch" else ""
                fun body(headers: Map<String, String>) = buildJsonObject {
                    if (authorized) {
                        put("code", "code"); put("credentialIssuerBaseUrl", issuer)
                    } else {
                        put("preAuthorizedCode", "code"); put("tokenEndpoint", "$issuer/token"); put("credentialIssuer", issuer)
                    }
                    put("tokenRequestHeaders", Json.encodeToJsonElement(headers))
                }.toString()
                val before = tokenCalls.get()
                for (headers in malformed) {
                    val response = client.post(path) { contentType(ContentType.Application.Json); setBody(body(headers)) }
                    assertEquals(HttpStatusCode.BadRequest, response.status, "$route: ${response.bodyAsText()}")
                    assertEquals(before, tokenCalls.get())
                }
                val response = client.post(path) { contentType(ContentType.Application.Json); setBody(body(valid)) }
                assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                assertEquals(before + 1, tokenCalls.get())
            }
            val malformedFullRequest = """{"offerJson":{"credential_issuer":"$issuer","credential_configuration_ids":["identity"]},
                "tokenRequestHeaders":{"OAuth-Client-Attestation":"manual-wia"}}"""
            val response = client.post("/wallet/manual/credentials/receive") {
                contentType(ContentType.Application.Json); setBody(malformedFullRequest)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
            assertEquals(4, tokenCalls.get())
        } finally {
            server.stop(0)
        }
    }
}

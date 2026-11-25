package id.walt.openid4vp.conformance

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import id.walt.openid4vp.conformance.adapter.VciWalletConformanceAdapter
import id.walt.openid4vp.conformance.adapter.resolveAuthorizationRedirect
import id.waltid.openid4vci.wallet.authorization.AuthorizationResponseParser
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class VciWalletAdapterTest {
    @Test
    fun callbackDoesNotInheritAuthorizationRequestParameters() {
        val callback = "http://127.0.0.1:7007/callback?code=test-code&state=expected&iss=https%3A%2F%2Fissuer.example"
        val resolved = resolveAuthorizationRedirect(
            "https://issuer.example/authorize?client_id=wallet&request_uri=urn%3Atest", callback)
        assertEquals(setOf("code", "state", "iss"), resolved.parameters.names())
        assertEquals("https://issuer.example/callback?code=x", resolveAuthorizationRedirect(
            "https://issuer.example/authorize?request_uri=old", "/callback?code=x").toString())
        assertEquals("test-code", AuthorizationResponseParser.parseAuthorizationResponse(
            resolved.toString(), "expected", "http://127.0.0.1:7007/callback", "https://issuer.example", true).code)
    }

    @Test
    fun deferredIssuanceResumesOnlyNewHandlesWithoutRedeemingTheOfferAgain() = runBlocking {
        val received = AtomicBoolean()
        val redemptions = AtomicInteger()
        val polls = AtomicInteger()
        val oldHandlePolls = AtomicInteger()
        withAdapter { backend, client, endpoint ->
            backend.createContext("/wallet/test/credentials/receive") { exchange ->
                when (exchange.requestURI.path) {
                    "/wallet/test/credentials/receive" -> {
                        redemptions.incrementAndGet()
                        received.set(true)
                        exchange.json(200, "{\"credentialIds\":[],\"deferredTransactionIds\":{\"config\":\"transaction\"}}")
                    }
                    "/wallet/test/credentials/receive/deferred" -> exchange.json(200,
                        if (received.get()) """[{"id":"old","intervalSeconds":0},{"id":"new","intervalSeconds":0}]"""
                        else """[{"id":"old","intervalSeconds":0}]""")
                    "/wallet/test/credentials/receive/deferred/new" -> exchange.json(200,
                        if (polls.incrementAndGet() == 1) """{"type":"deferred","credentials":[{"id":"new","intervalSeconds":0}]}"""
                        else """{"type":"stored","credentialIds":["stored"]}""")
                    else -> { oldHandlePolls.incrementAndGet(); exchange.json(500, "{}") }
                }
            }
            backend.start()
            val result = client.post(endpoint) { parameter("credential_offer", OFFER) }
            assertEquals(HttpStatusCode.OK, result.status)
            assertEquals(1, redemptions.get())
            assertEquals(2, polls.get())
            assertEquals(0, oldHandlePolls.get())
        }
    }

    @Test
    fun walletFailureIsNotReportedAsSuccessfulOfferProcessing() = runBlocking {
        withAdapter { backend, client, endpoint ->
            backend.createContext("/wallet/test/credentials/receive") { exchange ->
                if (exchange.requestURI.path.endsWith("/deferred")) exchange.json(200, "[]")
                else exchange.json(400, "{\"error\":\"invalid_grant\"}")
            }
            backend.start()
            assertEquals(HttpStatusCode.BadRequest, client.post(endpoint) {
                parameter("credential_offer", OFFER)
            }.status)
        }
    }

    private suspend fun withAdapter(block: suspend (HttpServer, HttpClient, String) -> Unit) {
        val backend = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = ServerSocket(0).use { it.localPort }
        val adapter = VciWalletConformanceAdapter(
            walletApiUrl = "http://127.0.0.1:${backend.address.port}", adapterPort = port, walletId = "test")
        HttpClient().use { client ->
            try {
                adapter.start(client)
                block(backend, client, "http://127.0.0.1:$port/credential-offer")
            } finally {
                adapter.stop()
                backend.stop(0)
            }
        }
    }

    private fun HttpExchange.json(status: Int, body: String) = use {
        requestBody.readBytes()
        responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }

    private companion object {
        const val OFFER = """{"credential_issuer":"https://issuer.example","credential_configuration_ids":["config"],"grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"test"}}}"""
    }
}

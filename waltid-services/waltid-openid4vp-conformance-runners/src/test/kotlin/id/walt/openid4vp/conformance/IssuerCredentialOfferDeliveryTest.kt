package id.walt.openid4vp.conformance

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import com.microsoft.playwright.TimeoutError
import id.walt.openid4vp.conformance.testplans.http.IssuerInterface
import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry
import id.walt.openid4vp.conformance.testplans.runner.IssuerCredentialOfferDelivery
import id.walt.openid4vp.conformance.testplans.runner.BrowserInteraction
import id.walt.openid4vp.conformance.testplans.runner.IssuerBrowserAutomationConfig
import id.walt.openid4vp.conformance.testplans.runner.IssuerConformanceBrowserAutomation
import id.walt.openid4vp.conformance.testplans.runner.browserDiagnosticUrl
import id.walt.openid4vp.conformance.testplans.runner.req.CredentialOfferAuthMethod
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuerCredentialOfferDeliveryTest {
    @Test
    fun browserDiagnosticsRemoveOAuthParametersAndUserInfo() {
        assertEquals(
            "https://issuer.example:9443/callback [parameters redacted]",
            browserDiagnosticUrl("https://user:password@issuer.example:9443/callback?code=secret&state=private#token"),
        )
        assertEquals("<non-HTTP URL>", browserDiagnosticUrl("data:text/html,secret"))
        assertEquals("<unavailable URL>", browserDiagnosticUrl("not a URL?secret"))
    }

    @Test
    fun issuerBrowserReportsFailedNavigationAfterLoginWithoutLeakingOAuthParameters() {
        val unavailablePort = ServerSocket(0).use { it.localPort }
        withBrowserStub { server, _ ->
            server.createContext("/authorize") { exchange ->
                exchange.respondHtml(
                    """
                    <html><body><form method="post" action="/login?state=private-state">
                    <input id="username" name="username"><input id="password" name="password" type="password">
                    <input id="kc-login" type="submit" value="Sign in">
                    </form></body></html>
                    """.trimIndent()
                )
            }
            server.createContext("/login") { exchange ->
                exchange.use {
                    it.requestBody.readBytes()
                    it.responseHeaders.add("Location", "http://127.0.0.1:$unavailablePort/broken-callback?code=private-code#private-fragment")
                    it.sendResponseHeaders(302, -1)
                }
            }
            server.start()
            val automation = IssuerConformanceBrowserAutomation(
                IssuerBrowserAutomationConfig(true, "runner-user", "runner-password", 10),
                "suite.invalid", 443,
            )
            val started = System.nanoTime()
            val error = assertFailsWith<IllegalStateException> {
                automation.complete(BrowserInteraction("http://localhost:${server.address.port}/authorize?state=initial-secret", "GET"))
            }
            val message = error.message.orEmpty()
            assertTrue(message.contains("FAILED GET http://127.0.0.1:$unavailablePort/broken-callback"), message)
            assertTrue(message.contains("ERR_CONNECTION_REFUSED"), message)
            assertTrue(message.contains("HTTP 302"), message)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 10_000, "Must detect the error without exhausting login timeout")
            for (secret in listOf("initial-secret", "private-state", "private-code", "private-fragment", "runner-password")) {
                assertFalse(message.contains(secret), "Diagnostic must not contain $secret")
            }
            assertNull(error.cause, "Raw Playwright call logs must not leak through the cause")
        }
    }

    @Test
    fun issuerBrowserCompletesLoginWithoutWaitingForAnUnfinishedImage() {
        for (method in listOf("GET", "POST")) {
            val loginSubmitted = AtomicBoolean(false)
            val imageRequested = CountDownLatch(1)
            withBrowserStub { server, release ->
                server.createContext("/authorize") { exchange ->
                    exchange.respondHtml(
                        """
                        <html><body>
                          <img src="/slow-image">
                          <form method="post" action="http://127.0.0.1:${server.address.port}/test/callback">
                            <input id="username" name="username">
                            <input id="password" name="password" type="password">
                            <input id="kc-login" type="submit" value="Sign in">
                          </form>
                        </body></html>
                        """.trimIndent()
                    )
                }
                server.createContext("/slow-image") { exchange ->
                    imageRequested.countDown()
                    try {
                        release.await()
                    } finally {
                        exchange.close()
                    }
                }
                server.createContext("/test/callback") { exchange ->
                    val body = exchange.requestBody.bufferedReader().use { it.readText() }
                    loginSubmitted.set(body.contains("username=runner-user") && body.contains("password=runner-password"))
                    exchange.respondHtml("<html><body><div id='submission_complete'>Done</div></body></html>")
                }
                server.start()
                val automation = IssuerConformanceBrowserAutomation(
                    IssuerBrowserAutomationConfig(true, "runner-user", "runner-password", 10),
                    "127.0.0.1", server.address.port,
                )
                // A different hostname distinguishes the login page from the suite callback.
                automation.complete(BrowserInteraction("http://localhost:${server.address.port}/authorize", method))
                assertEquals(0L, imageRequested.count)
                assertEquals(1L, release.count, "The image must still be blocked when login completes")
                assertTrue(loginSubmitted.get())
            }
        }
    }

    @Test
    fun issuerBrowserUsesConfiguredTimeoutForInitialNavigation() {
        withBrowserStub { server, release ->
            server.createContext("/authorize") { exchange ->
                try {
                    release.await()
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val automation = IssuerConformanceBrowserAutomation(
                IssuerBrowserAutomationConfig(true, "runner-user", "runner-password", 1),
                "127.0.0.1", server.address.port,
            )
            val error = assertFailsWith<TimeoutError> {
                automation.complete(BrowserInteraction("http://localhost:${server.address.port}/authorize", "GET"))
            }
            assertTrue(error.message.orEmpty().contains("1000ms"), "Initial navigation must use the configured timeout")
        }
    }

    @Test
    fun issuerManagementOffersUseOriginalProfileContractForBothFormatsAndGrantTypes() = runTest {
        for (profileId in listOf("identityCredentialSdJwt", "isoMdl")) {
            for (authMethod in listOf(CredentialOfferAuthMethod.AUTHORIZED, CredentialOfferAuthMethod.PRE_AUTHORIZED)) {
                for (staticTxCode in listOf(null, "493536", "test-code")) {
                    for (offer in listOf(
                        "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer",
                        "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.example%22%7D",
                    )) {
                        val preAuthorized = authMethod == CredentialOfferAuthMethod.PRE_AUTHORIZED
                        val expectedTxCode = staticTxCode.takeIf { preAuthorized }
                        val responseBody = buildJsonObject {
                            put("offerId", "test-offer")
                            put("profileId", profileId)
                            put("authMethod", authMethod.name)
                            put("expiresAt", 1_800_000_000_000L)
                            put("credentialOffer", offer)
                            put("issuerStateMode", "INCLUDE")
                            expectedTxCode?.let { put("txCodeValue", it) }
                        }
                        withIssuerManagementStub(201, responseBody.toString()) { issuer, capturedRequest ->
                            val response = issuer.createCredentialOffer(profileId, authMethod, staticTxCode)
                            val captured = capturedRequest.await()
                            assertEquals("POST", captured.method)
                            assertEquals("/issuer2/credential-offers", captured.path)
                            val request = captured.body
                            assertEquals(profileId, request["profileId"]!!.jsonPrimitive.content)
                            assertNull(request["credentials"])
                            assertEquals(authMethod.name, request["authMethod"]!!.jsonPrimitive.content)
                            // Leave issuer-state, value mode, expiry, and override defaults to the issuer.
                            assertEquals(
                                setOf("profileId", "authMethod") +
                                    if (expectedTxCode != null) setOf("txCode", "txCodeValue") else emptySet(),
                                request.keys,
                            )
                            if (expectedTxCode != null) {
                                assertEquals(expectedTxCode, request["txCodeValue"]!!.jsonPrimitive.content)
                                val txCode = request["txCode"]!!.jsonObject
                                assertEquals(if (expectedTxCode.all(Char::isDigit)) "numeric" else "text", txCode["input_mode"]!!.jsonPrimitive.content)
                                assertEquals(expectedTxCode.length, txCode["length"]!!.jsonPrimitive.int)
                                assertEquals("OpenID4VCI conformance transaction code", txCode["description"]!!.jsonPrimitive.content)
                            } else {
                                assertNull(request["txCode"])
                                assertNull(request["txCodeValue"])
                                assertNull(response.txCodeValue)
                            }
                            assertEquals(expectedTxCode, response.txCodeValue)
                            assertEquals("test-offer", response.offerId)
                            assertEquals(profileId, response.profileId)
                            assertEquals(authMethod.name, response.authMethod)
                            assertEquals(1_800_000_000_000L, response.expiresAt)
                            assertEquals(offer, response.credentialOffer)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun issuerManagementErrorsAreReportedBeforeOfferDeserialization() = runTest {
        val errorBody = """{"error":"Bad Request","message":"Unknown credential profile"}"""
        withIssuerManagementStub(400, errorBody) { issuer, _ ->
            val error = assertFailsWith<ClientRequestException> {
                issuer.createCredentialOffer("identityCredentialSdJwt", CredentialOfferAuthMethod.AUTHORIZED)
            }
            assertEquals(HttpStatusCode.BadRequest, error.response.status)
            assertEquals(errorBody, error.response.bodyAsText())
            assertTrue(error.message.contains("400"))
            assertTrue(error.message.contains("Unknown credential profile"))
        }
    }

    @Test
    fun issuerManagementServerErrorsRetainStatusAndBody() = runTest {
        val errorBody = """{"error":"Internal Server Error","message":"Offer storage unavailable"}"""
        withIssuerManagementStub(500, errorBody) { issuer, _ ->
            val error = assertFailsWith<ServerResponseException> {
                issuer.createCredentialOffer("isoMdl", CredentialOfferAuthMethod.PRE_AUTHORIZED)
            }
            assertEquals(HttpStatusCode.InternalServerError, error.response.status)
            assertEquals(errorBody, error.response.bodyAsText())
        }
    }

    @Test
    fun issuerManagementRejectsIncompatibleReceiptWithoutRetryingCreation() = runTest {
        // This is not a test of array management requests: the legacy helper must reject
        // a success receipt missing its required profileId instead of posting a fallback offer.
        val receipt = """{"offerId":"test-offer","authMethod":"AUTHORIZED","expiresAt":1800000000000,"credentialOffer":"openid-credential-offer://test"}"""
        val requestCount = AtomicInteger()
        withIssuerManagementStub(201, receipt, requestCount) { issuer, _ ->
            val error = assertFailsWith<JsonConvertException> {
                issuer.createCredentialOffer("identityCredentialSdJwt", CredentialOfferAuthMethod.AUTHORIZED)
            }
            assertTrue(error.message.orEmpty().contains("profileId"))
            assertEquals(1, requestCount.get())
        }
    }

    @Test
    fun twoClientsReceiveFreshOffersAtTheSameEndpointWithoutObservingRunning() = runTest {
        val delivery = IssuerCredentialOfferDelivery()
        val endpoint = "https://suite.example/test/multiple-clients/credential_offer"
        val delivered = mutableListOf<Pair<String, String>>()
        val log = mutableListOf(waitEvent("client-1"))

        assertTrue(delivery.deliverIfRequested(log) {
            delivered += endpoint to "fresh-offer-1"
            // The first HTTP delivery completes client 1 and starts client 2 synchronously.
            log += waitEvent("client-2")
        })
        assertTrue(delivery.deliverIfRequested(log) { delivered += endpoint to "fresh-offer-2" })
        assertFalse(delivery.deliverIfRequested(log) { error("Duplicate offer") })
        assertEquals(listOf(endpoint to "fresh-offer-1", endpoint to "fresh-offer-2"), delivered)
    }

    @Test
    fun repeatedPollsAndUnrelatedLogUpdatesDoNotDeliverAnotherOffer() = runTest {
        val delivery = IssuerCredentialOfferDelivery()
        val log = listOf(waitEvent("client-1"))
        assertTrue(delivery.deliverIfRequested(log) {})
        repeat(3) {
            assertFalse(delivery.deliverIfRequested(log) { error("Duplicate offer") })
        }
        val updatedLog = log + TestLogEntry(id = "tx-code", src = "VCIWaitForTxCode")
        assertFalse(delivery.deliverIfRequested(updatedLog) { error("Not a new offer request") })
    }

    @Test
    fun missingWaitEventOrEventIdDoesNotTriggerDelivery() = runTest {
        val delivery = IssuerCredentialOfferDelivery()
        assertFalse(delivery.deliverIfRequested(emptyList()) { error("No request") })
        assertFalse(delivery.deliverIfRequested(listOf(TestLogEntry(src = "VCIWaitForCredentialOffer"))) {
            error("No request ID")
        })
    }

    @Test
    fun failedDeliveryDoesNotMarkRequestAsDelivered() = runTest {
        val delivery = IssuerCredentialOfferDelivery()
        val log = listOf(waitEvent("client-1"))
        assertFailsWith<IllegalStateException> {
            delivery.deliverIfRequested(log) { error("Delivery failed") }
        }
        assertTrue(delivery.deliverIfRequested(log) {})
        assertFalse(delivery.deliverIfRequested(log) { error("Duplicate offer") })
    }

    private fun withBrowserStub(block: (HttpServer, CountDownLatch) -> Unit) {
        val executor = Executors.newCachedThreadPool()
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        try {
            block(server, release)
        } finally {
            release.countDown()
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun HttpExchange.respondHtml(html: String) = use {
        val bytes = html.toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.write(bytes)
    }

    private suspend fun withIssuerManagementStub(
        status: Int,
        responseBody: String,
        requestCount: AtomicInteger = AtomicInteger(),
        block: suspend (IssuerInterface, CompletableDeferred<CapturedOfferRequest>) -> Unit,
    ) {
        val request = CompletableDeferred<CapturedOfferRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/issuer2/credential-offers") { exchange ->
            exchange.use {
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                requestCount.incrementAndGet()
                request.complete(CapturedOfferRequest(exchange.requestMethod, exchange.requestURI.path, Json.parseToJsonElement(body).jsonObject))
                val bytes = responseBody.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.start()
        try {
            IssuerInterface("http://127.0.0.1:${server.address.port}").use { issuer -> block(issuer, request) }
        } finally {
            server.stop(0)
        }
    }

    private data class CapturedOfferRequest(val method: String, val path: String, val body: JsonObject)

    private fun waitEvent(id: String) = TestLogEntry(id = id, src = "VCIWaitForCredentialOffer")
}

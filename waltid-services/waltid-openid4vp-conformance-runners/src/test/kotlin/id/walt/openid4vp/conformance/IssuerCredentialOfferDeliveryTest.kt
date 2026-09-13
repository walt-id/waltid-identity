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
import id.walt.openid4vp.conformance.testplans.runner.req.CredentialOfferAuthMethod
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuerCredentialOfferDeliveryTest {
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
    fun issuerManagementOffersUseCredentialsForBothFormatsAndGrantTypes() = runTest {
        val profiles = mapOf(
            "identityCredentialSdJwt" to "identity_credential",
            "isoMdl" to "org.iso.18013.5.1.mDL",
        )
        for ((profileId, configurationId) in profiles) {
            for (authMethod in listOf(CredentialOfferAuthMethod.AUTHORIZED, CredentialOfferAuthMethod.PRE_AUTHORIZED)) {
                val preAuthorized = authMethod == CredentialOfferAuthMethod.PRE_AUTHORIZED
                val responseBody = buildJsonObject {
                    put("offerId", "test-offer")
                    putJsonArray("credentials") {
                        addJsonObject {
                            put("profileId", profileId)
                            put("credentialConfigurationId", configurationId)
                        }
                    }
                    put("authMethod", authMethod.name)
                    put("expiresAt", 1_800_000_000_000L)
                    put("credentialOffer", "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer")
                    put("issuerStateMode", "INCLUDE")
                    if (preAuthorized) put("txCodeValue", "493536")
                }
                withIssuerManagementStub(201, responseBody.toString()) { issuer, capturedRequest ->
                    val response = issuer.createCredentialOffer(profileId, authMethod, staticTxCode = "493536")
                    val request = capturedRequest.await()
                    assertNull(request["profileId"], "The API no longer accepts a top-level profileId")
                    val credential = request["credentials"]!!.jsonArray.single().jsonObject
                    assertEquals(profileId, credential["profileId"]!!.jsonPrimitive.content)
                    assertEquals(authMethod.name, request["authMethod"]!!.jsonPrimitive.content)
                    if (preAuthorized) {
                        assertEquals("493536", request["txCodeValue"]!!.jsonPrimitive.content)
                        val txCode = request["txCode"]!!.jsonObject
                        assertEquals("numeric", txCode["input_mode"]!!.jsonPrimitive.content)
                        assertEquals(6, txCode["length"]!!.jsonPrimitive.int)
                        assertEquals("493536", response.txCodeValue)
                    } else {
                        assertNull(request["txCode"])
                        assertNull(request["txCodeValue"])
                        assertNull(response.txCodeValue)
                    }
                    assertEquals("test-offer", response.offerId)
                    assertEquals(profileId, response.credentials.single().profileId)
                    assertEquals(configurationId, response.credentials.single().credentialConfigurationId)
                    assertEquals(responseBody["credentialOffer"]!!.jsonPrimitive.content, response.credentialOffer)
                }
            }
        }
    }

    @Test
    fun issuerManagementErrorsAreReportedBeforeOfferDeserialization() = runTest {
        val errorBody = """{"error":"Bad Request","message":"Field 'credentials' is required"}"""
        withIssuerManagementStub(400, errorBody) { issuer, _ ->
            val error = assertFailsWith<ClientRequestException> {
                issuer.createCredentialOffer("identityCredentialSdJwt", CredentialOfferAuthMethod.AUTHORIZED)
            }
            assertEquals(HttpStatusCode.BadRequest, error.response.status)
            assertEquals(errorBody, error.response.bodyAsText())
            assertTrue(error.message.contains("400"))
            assertTrue(error.message.contains("credentials"))
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
        block: suspend (IssuerInterface, CompletableDeferred<JsonObject>) -> Unit,
    ) {
        val request = CompletableDeferred<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/issuer2/credential-offers") { exchange ->
            exchange.use {
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                request.complete(Json.parseToJsonElement(body).jsonObject)
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

    private fun waitEvent(id: String) = TestLogEntry(id = id, src = "VCIWaitForCredentialOffer")
}

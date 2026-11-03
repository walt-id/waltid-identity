package id.walt.openid4vp.conformance

import com.sun.net.httpserver.HttpServer
import id.walt.openid4vp.conformance.testplans.*
import id.walt.openid4vp.conformance.testplans.http.IssuerDeferredProxy
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantMatrix
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class IssuerDeferredProxyTest {
    private class Fixture(val failApproval: Boolean = false, val approvalDelay: Long = 0) : AutoCloseable {
        val events = CopyOnWriteArrayList<String>()
        val approved = mutableSetOf<String>()
        val evidence = Files.createTempFile("issuer-deferred", ".jsonl")
        val client = HttpClient.newHttpClient()
        val ids = AtomicInteger()
        val opaqueRequest = "header.encrypted.request.with.tag".toByteArray()
        val opaquePending = "header.encrypted.pending.with.tag".toByteArray()
        val opaqueCredentials = "header.encrypted.credentials.with.tag".toByteArray()
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { call ->
                val path = call.requestURI.path
                val body = call.requestBody.readAllBytes()
                val id = IssuerDeferredProxy.sessionSubject(call.requestHeaders.getFirst("Authorization"))
                val (status, response) = when {
                    path == "/issuer2/credential-offers" -> {
                        assertTrue(Json.parseToJsonElement(body.decodeToString()).jsonObject.getValue("deferred").jsonPrimitive.boolean)
                        201 to "{\"offerId\":\"offer-${ids.incrementAndGet()}\"}".toByteArray()
                    }
                    path.endsWith("/deferred/complete") -> {
                        val offer = path.split('/')[3]
                        events += "approve-$offer"
                        if (approvalDelay > 0) Thread.sleep(approvalDelay)
                        if (failApproval) 409 to ByteArray(0) else {
                            approved += offer
                            204 to ByteArray(0)
                        }
                    }
                    path == "/openid4vci/credential" -> {
                        events += "initial-$id"
                        assertContentEquals(opaqueRequest, body)
                        assertEquals("original-dpop-proof", call.requestHeaders.getFirst("DPoP"))
                        assertEquals("application/jwt", call.requestHeaders.getFirst("Content-Type"))
                        assertEquals("issuer.example", call.requestHeaders.getFirst("X-Forwarded-Host"))
                        202 to opaquePending
                    }
                    path == "/openid4vci/deferred_credential" -> {
                        events += "collect-$id"
                        if (id in approved) 200 to opaqueCredentials else 202 to opaquePending
                    }
                    else -> 200 to body
                }
                call.responseHeaders.add("Content-Type", "application/jwt")
                call.responseHeaders.add("DPoP-Nonce", "original-server-nonce")
                call.sendResponseHeaders(status, if (response.isEmpty()) -1 else response.size.toLong())
                if (response.isNotEmpty()) call.responseBody.write(response)
                call.close()
            }
            start()
        }
        val proxy = IssuerDeferredProxy(URI("http://127.0.0.1:${upstream.address.port}"), 0, evidence,
            Duration.ofMillis(if (approvalDelay > 0) 100 else 5000)).apply { start() }

        fun post(path: String, body: ByteArray = opaqueRequest, offer: String? = null): HttpResponse<ByteArray> {
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${proxy.port}$path"))
                .timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .header("Content-Type", "application/jwt").header("DPoP", "original-dpop-proof")
                .header("X-Forwarded-Host", "issuer.example")
            offer?.let {
                val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"$it\"}".toByteArray())
                request.header("Authorization", "DPoP header.$payload.signature")
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
        }

        fun offer(): String = Json.parseToJsonElement(post("/issuer2/credential-offers", "{\"deferred\":true}".toByteArray())
            .body().decodeToString()).jsonObject.getValue("offerId").jsonPrimitive.content

        override fun close() {
            proxy.close()
            upstream.stop(0)
            client.close()
            Files.deleteIfExists(evidence)
        }
    }

    @Test
    fun encryptedPayloadsAndDpopPassThroughWhileApprovalPrecedesDelivery() = Fixture().use { fixture ->
        val first = fixture.offer()
        val second = fixture.offer()
        val initial = fixture.post("/openid4vci/credential", offer = second)
        assertEquals(202, initial.statusCode())
        assertContentEquals(fixture.opaquePending, initial.body())
        assertEquals("original-server-nonce", initial.headers().firstValue("DPoP-Nonce").orElseThrow())
        assertEquals(listOf("initial-$second", "approve-$second"), fixture.events.toList())
        assertFalse(first in fixture.approved)
        assertTrue(second in fixture.approved)
        assertContentEquals(fixture.opaqueCredentials, fixture.post("/openid4vci/deferred_credential", offer = second).body())
        readIssuerDeferredEvidence(fixture.evidence, listOf(second)).requireComplete()
        assertFailsWith<IllegalArgumentException> { readIssuerDeferredEvidence(fixture.evidence, listOf(first, second)).requireComplete() }
        val receipts = Files.readString(fixture.evidence)
        assertFalse(receipts.contains("signature"))
        assertFalse(receipts.contains(second))
        assertFalse(receipts.contains("encrypted"))
    }

    @Test
    fun approvalFailurePreservesOriginalPendingResponseButFailsEvidence() = Fixture(failApproval = true).use { fixture ->
        val offer = fixture.offer()
        assertEquals(202, fixture.post("/openid4vci/credential", offer = offer).statusCode())
        assertEquals(202, fixture.post("/openid4vci/deferred_credential", offer = offer).statusCode())
        val evidence = readIssuerDeferredEvidence(fixture.evidence, listOf(offer))
        assertEquals(1, evidence.failures)
        assertFailsWith<IllegalArgumentException> { evidence.requireComplete() }
    }

    @Test
    fun approvalTimeoutCannotProducePassingEvidence() = Fixture(approvalDelay = 300).use { fixture ->
        val offer = fixture.offer()
        assertEquals(502, fixture.post("/openid4vci/credential", offer = offer).statusCode())
        assertFailsWith<IllegalArgumentException> { readIssuerDeferredEvidence(fixture.evidence, listOf(offer)).requireComplete() }
    }

    @Test
    fun unregisteredTrafficCannotBeApprovedAndOrdinaryResponsesPassThrough() = Fixture().use { fixture ->
        assertEquals(502, fixture.post("/openid4vci/credential", offer = "unknown").statusCode())
        assertTrue(fixture.approved.isEmpty())
        assertContentEquals(fixture.opaqueRequest, fixture.post("/ordinary").body())
    }

    @Test
    fun approvalBeforeInitialResponseDoesNotCountAsDeferredCoverage() {
        val path = Files.createTempFile("deferred-order", ".jsonl")
        try {
            val key = deferredOfferKey("offer")
            Files.writeString(path, listOf("registered", "approved204", "initial202", "collected200").joinToString("\n") {
                """{"offerKey":"$key","event":"$it"}"""
            })
            val evidence = readIssuerDeferredEvidence(path, listOf("offer"))
            assertEquals(1, evidence.collected200)
            assertFailsWith<IllegalArgumentException> { evidence.requireComplete() }
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun deferredMatrixIsIssuerInitiatedAndDoesNotSendRunnerSettingsToSuite() {
        val variants = IssuerVariantMatrix.deferred()
        assertEquals(12, variants.size)
        assertEquals(12, variants.map { it.id }.distinct().size)
        variants.forEach { variant ->
            assertEquals("issuer_initiated", variant.authorizationCodeFlowVariant)
            assertEquals(IssuerIssuanceMode.DEFERRED, variant.issuanceMode)
            assertEquals("oid4vci-1_0-issuer-test-plan", variant.conformanceTestPlanName)
            if (variant.isHaip) assertEquals("oid4vci-1_0-issuer-haip-test-plan",
                variant.copy(issuanceMode = IssuerIssuanceMode.IMMEDIATE).conformanceTestPlanName)
            assertEquals("deferred", variant.toJsonObject()["issuance_mode"]?.jsonPrimitive?.content)
            assertFalse("issuance_mode" in variant.testPlanCreationVariant())
            assertFalse("credential_proof_type" in variant.copy(credentialProofType = "jwt").testPlanCreationVariant())
            assertNotEquals(variant.copy(issuanceMode = IssuerIssuanceMode.IMMEDIATE).id, variant.id)
        }
    }
}

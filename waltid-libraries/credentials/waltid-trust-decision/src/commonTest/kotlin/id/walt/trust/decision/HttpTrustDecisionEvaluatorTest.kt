package id.walt.trust.decision

import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class HttpTrustDecisionEvaluatorTest {

    private val url = "https://registry.example/trust-registry/resolve/certificate-chain"
    private val at = Instant.parse("2026-11-09T10:00:00Z")
    private val requests = mutableListOf<HttpRequestData>()

    private fun evaluator(
        headers: suspend () -> Map<String, String> = { emptyMap() },
        handler: MockRequestHandler,
    ): HttpTrustDecisionEvaluator {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val fetcher = WebDataFetcher.wrapping(HttpClient(engine), WebDataFetcherId.TRUST_DECISION_RESOLVE.name)
        return HttpTrustDecisionEvaluator(url, headers, fetcher)
    }

    private fun request(vararg types: TrustedEntityType) = TrustDecisionRequest(
        purpose = TrustPurpose.ISSUER_WUA_SIGNER,
        subject = TrustSubject.CertificateChain(listOf(TestCertificates.LEAF_DER_BASE64)),
        expectedEntityTypes = types.toSet(),
        expectedServiceType = "http://uri.etsi.org/19602/SvcType/WalletProvider",
        at = at,
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun MockRequestHandleScope.decision(code: TrustDecisionCode, freshness: FreshnessState = FreshnessState.FRESH) =
        json(Json.encodeToString(TrustDecision(code, sourceFreshness = freshness)))

    @Test
    fun postsResolveRequestWithHeadersAndReturnsRemoteDecision() = runTest {
        val evaluator = evaluator({ mapOf(HttpHeaders.Authorization to "Bearer test-token") }) {
            decision(TrustDecisionCode.TRUSTED)
        }

        val decision = evaluator.evaluate(request(TrustedEntityType.WALLET_PROVIDER))

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals(FreshnessState.FRESH, decision.sourceFreshness)
        val sent = requests.single()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals(url, sent.url.toString())
        assertEquals("Bearer test-token", sent.headers[HttpHeaders.Authorization])
        val body = Json.parseToJsonElement((sent.body as TextContent).text).jsonObject
        assertEquals(TestCertificates.LEAF_DER_BASE64, body.getValue("certificateChainPemOrDer").jsonArray.single().jsonPrimitive.content)
        assertEquals(at.toString(), body.getValue("instant").jsonPrimitive.content)
        assertEquals("WALLET_PROVIDER", body.getValue("expectedEntityType").jsonPrimitive.content)
        assertEquals("http://uri.etsi.org/19602/SvcType/WalletProvider", body.getValue("expectedServiceType").jsonPrimitive.content)
    }

    @Test
    fun staleSourceDecisionIsPassedThrough() = runTest {
        val decision = evaluator { decision(TrustDecisionCode.STALE_SOURCE, FreshnessState.EXPIRED) }
            .evaluate(request(TrustedEntityType.WALLET_PROVIDER))

        assertEquals(TrustDecisionCode.STALE_SOURCE, decision.decision)
        assertEquals(FreshnessState.EXPIRED, decision.sourceFreshness)
    }

    @Test
    fun nonSuccessStatusIsUnknownAndNamesTheStatus() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.ServiceUnavailable)) {
            val decision = evaluator { json("{}", status) }
                .evaluate(request(TrustedEntityType.WALLET_PROVIDER))

            assertEquals(TrustDecisionCode.UNKNOWN, decision.decision)
            assertTrue(decision.warnings.single().contains("${status.value}"), decision.warnings.toString())
        }
    }

    @Test
    fun failingHeaderProviderIsUnknownAndSendsNothing() = runTest {
        val decision = evaluator(headers = { error("token source is down") }) { decision(TrustDecisionCode.TRUSTED) }
            .evaluate(request(TrustedEntityType.WALLET_PROVIDER))

        assertEquals(TrustDecisionCode.UNKNOWN, decision.decision)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun minimalLegacyAnswerMeetsTrustedOnlyWithoutExpectedTypes() = runTest {
        val evaluator = evaluator { json("""{"decision":"TRUSTED"}""") }
        val typed = request(TrustedEntityType.WALLET_PROVIDER)

        assertIs<TrustCheck.Accepted>(evaluator.check(request(), TrustRequirement.Trusted))
        assertIs<TrustCheck.Rejected>(evaluator.check(typed, TrustRequirement.Trusted))
        assertIs<TrustCheck.Rejected>(evaluator.check(typed, TrustRequirement.RegistryFresh()))
    }

    @Test
    fun unknownFreshnessValueIsReadAsUnknown() = runTest {
        val decision = evaluator { json("""{"decision":"TRUSTED","sourceFreshness":"EVERGREEN"}""") }
            .evaluate(request(TrustedEntityType.WALLET_PROVIDER))

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals(FreshnessState.UNKNOWN, decision.sourceFreshness)
    }

    @Test
    fun cancellationIsNotTurnedIntoADecision() = runTest {
        val evaluator = evaluator { throw CancellationException("caller went away") }

        assertFailsWith<CancellationException> { evaluator.evaluate(request(TrustedEntityType.WALLET_PROVIDER)) }
    }

    @Test
    fun unreachableResolverIsUnknown() = runTest {
        val decision = evaluator { error("connection refused") }
            .evaluate(request(TrustedEntityType.WALLET_PROVIDER))

        assertEquals(TrustDecisionCode.UNKNOWN, decision.decision)
    }

    @Test
    fun answerThatIsNotADecisionIsProcessingError() = runTest {
        val decision = evaluator { json("""{"decision":"MAYBE"}""") }
            .evaluate(request(TrustedEntityType.WALLET_PROVIDER))

        assertEquals(TrustDecisionCode.PROCESSING_ERROR, decision.decision)
    }

    @Test
    fun asksOncePerExpectedTypeUntilTrusted() = runTest {
        val evaluator = evaluator { sent ->
            val type = Json.parseToJsonElement((sent.body as TextContent).text).jsonObject["expectedEntityType"]?.jsonPrimitive?.content
            // Types are asked in declaration order: ACCESS_CERTIFICATE_PROVIDER first, then RELYING_PARTY_PROVIDER
            decision(if (type == TrustedEntityType.RELYING_PARTY_PROVIDER.name) TrustDecisionCode.TRUSTED else TrustDecisionCode.NOT_TRUSTED)
        }

        val decision = evaluator.evaluate(
            request(TrustedEntityType.RELYING_PARTY_PROVIDER, TrustedEntityType.ACCESS_CERTIFICATE_PROVIDER)
        )

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals(2, requests.size)
    }
}

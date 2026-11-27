package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.httpdata.TestRunResult
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.Oid4vpWalletVariantPlan
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletVariant
import id.walt.openid4vp.conformance.testplans.runner.WalletTestPlanRunner
import id.waltid.openid4vp.wallet.DcApiCredentialResponse
import id.waltid.openid4vp.wallet.UnsupportedDcApiProtocolException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DcApiWalletRelayTest {
    private fun interaction(submit: String = "https://suite.example/test/id/submit"): TestRunResult {
        val entry = Json.parseToJsonElement("""{
            "request":{"digital":{"requests":[{"protocol":"openid4vp-v1-unsigned","data":{"expected_origins":["https://untrusted.example"]}}]}},
            "submitUrl":"$submit"
        }""")
        return TestRunResult("id", "module", Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(0),
            TestRunResult.Owner("local", "test"), TestRunResult.Browser(listOf(entry), showQrCode = false),
            JsonObject(emptyMap()))
    }

    private fun runner(client: HttpClient, present: suspend (String, JsonObject, String) -> DcApiCredentialResponse) =
        WalletTestPlanRunner(Oid4vpWalletVariantPlan(
            WalletVariant("sd_jwt_vc", "x509_hash", "request_uri_signed", "dc_api.jwt", "haip"),
            "https://wallet.example", "suite.example", 443), client, "suite.example", 443, present)

    @Test
    fun encryptedResponseIsRelayedUnchangedWithHarnessControlledOrigin() = runTest {
        var posts = 0
        HttpClient(MockEngine { request ->
            posts++
            assertEquals("https://suite.example/test/id/submit", request.url.toString())
            assertEquals(Json.parseToJsonElement("""{"protocol":"openid4vp-v1-unsigned","data":{"response":"a.b.c.d.e"}}"""),
                Json.parseToJsonElement((request.body as TextContent).text))
            respond("ok")
        }).use { client ->
            val result = runner(client) { protocol, _, origin ->
                assertEquals("https://suite.example", origin)
                DcApiCredentialResponse(protocol, buildJsonObject { put("response", "a.b.c.d.e") })
            }.deliverDcApiRequest(interaction())
            assertEquals("PRESENTED", result)
        }
        assertEquals(1, posts)
    }

    @Test
    fun foreignSubmissionUrlFailsBeforeReadingTheWallet() = runTest {
        HttpClient(MockEngine { error("Must not post") }).use { client ->
            assertFailsWith<IllegalArgumentException> {
                runner(client) { _, _, _ -> error("Must not invoke SDK") }
                    .deliverDcApiRequest(interaction("https://foreign.example/submit"))
            }
        }
    }

    @Test
    fun unsupportedProtocolAndCancellationAreNotReportedAsOrdinaryRejection() = runTest {
        HttpClient(MockEngine { error("Must not post") }).use { client ->
            assertFailsWith<UnsupportedDcApiProtocolException> {
                runner(client) { _, _, _ -> throw UnsupportedDcApiProtocolException("openid4vp-v1-multisigned") }
                    .deliverDcApiRequest(interaction())
            }
            assertFailsWith<CancellationException> {
                runner(client) { _, _, _ -> throw CancellationException("parent") }
                    .deliverDcApiRequest(interaction())
            }
        }
    }
}

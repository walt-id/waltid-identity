package id.waltid.openid4vp.wallet

import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.config.RequestConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.HttpStatusCode
import io.ktor.http.parametersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject

class WalletPresentResponseTest {
    @Test
    fun lostDirectPostReceiptIsUncertainAndParentCancellationPropagates() = runTest {
        var posts = 0
        val transport = WebDataFetcher.wrapping(HttpClient(MockEngine { _ ->
            posts++
            if (posts == 1) throw IOException("private peer diagnostic")
            throw CancellationException("Parent cancelled")
        }) { install(HttpRequestRetry) { retryOnExceptionOrServerErrors(maxRetries = 2); delayMillis { 0L } } }, "wallet-response-test")
        transport.use {
            val form = parametersOf("vp_token", "test-presentation")
            val unknown = assertFailsWith<PresentationDeliveryUncertainException> {
                WalletPresentFunctionality2.postFormResponse("https://verifier.example/response", form, it)
            }
            assertFalse(unknown.message.orEmpty().contains("private peer diagnostic"))
            assertEquals(1, posts)
            assertFailsWith<CancellationException> {
                WalletPresentFunctionality2.postFormResponse("https://verifier.example/response", form, it)
            }
            assertEquals(2, posts)
        }
    }

    @Test
    fun disallowedDestinationSendsNothingAndExplicitPeerRejectionReturnsAReceipt() = runTest {
        var requests = 0
        val config = WebDataFetchingConfiguration(url = UrlConfiguration(hosts = AllowList(whitelist = listOf("verifier.example"))),
            request = RequestConfiguration(expectSuccess = true))
        val transport = WebDataFetcher.wrapping(HttpClient(MockEngine {
            requests++
            respond("declined", HttpStatusCode.BadRequest)
        }), "wallet-response-policy-test", config)
        transport.use {
            val fields = parametersOf("vp_token", "synthetic")
            assertFailsWith<IllegalArgumentException> {
                WalletPresentFunctionality2.postFormResponse("https://other.example/result", fields, it)
            }
            assertEquals(0, requests)
            val receipt = WalletPresentFunctionality2.postFormResponse("https://verifier.example/result", fields, it)
            assertEquals(false, receipt.transmissionSuccess)
            assertEquals(1, requests)
        }
    }

    @Test
    fun `empty successful direct-post response is accepted`() {
        val result = WalletPresentFunctionality2.directPostResult(success = true, responseBody = "")

        assertTrue(result.transmissionSuccess == true)
        assertEquals(JsonObject(emptyMap()), result.verifierResponse)
        assertNull(result.redirectTo)
    }

    @Test
    fun `successful direct-post response preserves redirect URI`() {
        val result = WalletPresentFunctionality2.directPostResult(
            success = true,
            responseBody = """{"redirect_uri":"https://verifier.example/complete"}""",
        )

        assertEquals("https://verifier.example/complete", result.redirectTo)
    }

    @Test
    fun `plain-text failure response preserves transmission result`() {
        val result = WalletPresentFunctionality2.directPostResult(success = false, responseBody = "upstream failure")

        assertEquals(false, result.transmissionSuccess)
        assertNull(result.redirectTo)
    }
}

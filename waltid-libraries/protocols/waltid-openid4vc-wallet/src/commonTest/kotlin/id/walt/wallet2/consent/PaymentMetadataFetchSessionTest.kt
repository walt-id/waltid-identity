package id.walt.wallet2.consent

import id.walt.crypto.utils.ShaUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class PaymentMetadataFetchSessionTest {
    private fun networkTest(block: suspend () -> Unit) = runTest { withContext(Dispatchers.Default) { block() } }
    private val bytes = "{\"value\":1}".encodeToByteArray()
    private val integrity = "sha256-" + ShaUtils.sha256Base64Url(bytes)

    @Test
    fun integrityChecksExactRetrievedBytesAndFreezesTheResolution() = networkTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond(bytes, headers = headersOf(HttpHeaders.ContentType, "application/json")) })
        try {
            PaymentMetadataFetchSession(client).use { session ->
                val document = session.read("https://issuer.example/vct", listOf(integrity))
                assertEquals(document, session.read("https://issuer.example/vct"))
                assertEquals(1, calls)
                val mismatch = assertFailsWith<PaymentConsentException> {
                    session.read("https://issuer.example/vct", listOf("sha256-" + ShaUtils.sha256Base64Url("other".encodeToByteArray())))
                }
                assertEquals(PaymentConsentFailure.INTEGRITY_MISMATCH, mismatch.reason)
            }
        } finally { client.close() }
        verifyMetadataIntegrity(bytes, integrity)
        assertFailsWith<PaymentConsentException> { verifyMetadataIntegrity("{ \"value\":1}".encodeToByteArray(), integrity) }
    }

    @Test
    fun rejectsInsecureSourcesRedirectDowngradesAndRedirectLoops() = networkTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://issuer.example/downstream"))
        })
        try {
            PaymentMetadataFetchSession(client).use { session ->
                for (url in listOf("http://issuer.example/vct", "https://user:password@issuer.example/vct", "https://issuer.example/vct#fragment")) {
                    assertFailsWith<PaymentConsentException> { session.read(url) }
                }
                assertEquals(0, calls)
                assertFailsWith<PaymentConsentException> { session.read("https://issuer.example/vct") }
                assertEquals(1, calls)
            }
        } finally { client.close() }
        var loops = 0
        val looping = HttpClient(MockEngine {
            loops++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://issuer.example/loop"))
        })
        try {
            PaymentMetadataFetchSession(looping).use { session -> assertFailsWith<PaymentConsentException> { session.read("https://issuer.example/vct") } }
            assertEquals(4, loops)
        } finally { looping.close() }
    }

    @Test
    fun configuredUrlPolicyAppliesBeforeInitialAndRedirectRequests() = networkTest {
        val requested = mutableListOf<String>()
        val checked = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requested += request.url.toString()
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://blocked.example/metadata"))
        })
        try {
            PaymentMetadataFetchSession(client, requireUrlAllowed = { url ->
                checked += url
                require(Url(url).host == "issuer.example")
            }).use { session ->
                assertFailsWith<PaymentConsentException> { session.read("https://blocked.example/vct") }
                assertTrue(requested.isEmpty())
                assertFailsWith<PaymentConsentException> { session.read("https://issuer.example/vct") }
                assertEquals(listOf("https://issuer.example/vct"), requested)
                assertEquals(listOf("https://blocked.example/vct", "https://issuer.example/vct", "https://blocked.example/metadata"), checked)
            }
        } finally { client.close() }
    }

    @Test
    fun boundsDocumentCountAndStreamedBodyWithoutContentLength() = networkTest {
        val small = HttpClient(MockEngine { respond("{}") })
        try {
            PaymentMetadataFetchSession(small).use { session ->
                repeat(3) { session.read("https://issuer.example/$it") }
                assertFailsWith<PaymentConsentException> { session.read("https://issuer.example/fourth") }
            }
        } finally { small.close() }
        val oversized = HttpClient(MockEngine { respond("x".repeat(262_145)) })
        try {
            PaymentMetadataFetchSession(oversized).use { session ->
                assertFailsWith<PaymentConsentException> { session.read("https://issuer.example/oversized") }
            }
        } finally { oversized.close() }
    }

    @Test
    fun permitsOnlyExplicitLoopbackExceptionsAndPropagatesCancellation() = networkTest {
        val client = HttpClient(MockEngine { respond("true") })
        try {
            PaymentMetadataFetchSession(client, allowHttpLoopback = true).use { session ->
                assertEquals(JsonPrimitive(true), session.read("http://127.0.0.1:8080/vct"))
                assertFailsWith<PaymentConsentException> { session.read("http://issuer.example/vct") }
            }
        } finally { client.close() }
        val cancelled = HttpClient(MockEngine { throw CancellationException("User cancelled") })
        try {
            PaymentMetadataFetchSession(cancelled).use { session ->
                assertFailsWith<CancellationException> { session.read("https://issuer.example/vct") }
            }
        } finally { cancelled.close() }
    }
    @Test
    fun relativeHttpsRedirectsResolveBeforeIntegrityVerification() = networkTest {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath == "/types/card") respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "../metadata"))
            else respond(bytes)
        })
        try {
            PaymentMetadataFetchSession(client).use { session ->
                session.read("https://issuer.example/types/card", listOf(integrity))
                assertEquals(listOf("/types/card", "/metadata"), paths)
            }
        } finally { client.close() }
    }

    @Test
    fun strongestIntegrityAlternativeMustMatchAndMalformedReferencesFailClosed() {
        val base64 = kotlin.io.encoding.Base64.Default
        val strong = "sha512-" + base64.encode(org.kotlincrypto.hash.sha2.SHA512().digest(bytes))
        val wrongStrong = "sha512-" + base64.encode(org.kotlincrypto.hash.sha2.SHA512().digest(byteArrayOf(1)))
        verifyMetadataIntegrity(bytes, "$integrity $strong")
        verifyMetadataIntegrity(bytes, "$wrongStrong $strong")
        verifyMetadataIntegrity(bytes, "sha1-abc sha256-! $strong?option?another")
        assertEquals(PaymentConsentFailure.INTEGRITY_MISMATCH,
            assertFailsWith<PaymentConsentException> { verifyMetadataIntegrity(bytes, "$integrity $wrongStrong") }.reason)
        // A syntactically valid stronger digest cannot be discarded to use the weaker matching one.
        assertEquals(PaymentConsentFailure.INTEGRITY_MISMATCH,
            assertFailsWith<PaymentConsentException> { verifyMetadataIntegrity(bytes, "$integrity sha512-YQ==") }.reason)
        for (invalid in listOf("", "sha1-abc", "sha256-!")) {
            assertEquals(PaymentConsentFailure.INVALID_METADATA,
                assertFailsWith<PaymentConsentException> { verifyMetadataIntegrity(bytes, invalid) }.reason)
        }
    }

}

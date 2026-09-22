package id.walt.itb

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ItbRestClientTest {
    private val apiUrl = Url("https://testbed.example/itb/api/rest")

    @Test
    fun `XML reports request exactly one media type even with wallet JSON negotiation installed`() = runBlocking<Unit> {
        val session = "00000000-0000-0000-0000-000000000001"
        val xml = javaClass.getResource("/itb/vci006-success.xml")!!.readText()
        HttpClient(MockEngine { request ->
            assertEquals(listOf("application/xml"), request.headers.getAll(HttpHeaders.Accept))
            respond(xml, headers = headersOf(HttpHeaders.ContentType, "application/xml"))
        }) {
            install(ContentNegotiation) { json() }
        }.use { client ->
            assertTrue(ItbRestClient(client, apiUrl, "secret").report("tc_vci_006", session).passed)
        }
    }

    @Test
    fun `starts explicit cases with sequential execution and no implicit wait`() = runBlocking<Unit> {
        val engine = MockEngine { request ->
            assertEquals("/itb/api/rest/tests/start", request.url.encodedPath)
            assertEquals("organisation-secret", request.headers["ITB-API-KEY"])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(JsonPrimitive(true), body["forceSequentialExecution"])
            assertEquals(JsonPrimitive(false), body["waitForCompletion"])
            assertEquals(JsonArray(listOf(JsonPrimitive("cs01v1"))), body["testSuite"])
            assertEquals(JsonArray(listOf(JsonPrimitive("tc_vci_006"))), body["testCase"])
            respond("""{"createdSessions":[{"testSuite":"cs01v1","testCase":"tc_vci_006","session":"run-1"}]}""")
        }
        HttpClient(engine).use {
            val created = ItbRestClient(it, apiUrl, "organisation-secret")
                .start("system-secret", "actor-secret", "cs01v1", listOf("tc_vci_006"))
            assertEquals("run-1", created.single().session)
        }
    }

    @Test
    fun `status requires exact session inventory and distinguishes running success`() = runBlocking<Unit> {
        var call = 0
        val responses = listOf(
            """{"sessions":[{"session":"run-1","result":"SUCCESS","startTime":"2026-09-21T14:00:00Z"}]}""",
            """{"sessions":[]}""",
            """{"sessions":[{"session":"another-run","result":"SUCCESS","startTime":"2026-09-21T14:00:00Z"}]}""",
        )
        HttpClient(MockEngine { respond(responses[call++]) }).use {
            val client = ItbRestClient(it, apiUrl, "secret")
            assertFalse(client.status(listOf("run-1")).single().isComplete)
            assertFailsWith<IllegalArgumentException> { client.status(listOf("run-1")) }
            assertFailsWith<IllegalArgumentException> { client.status(listOf("run-1")) }
        }
    }

    @Test
    fun `stop sends only the explicitly owned session selection`() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            assertEquals("/itb/api/rest/tests/stop", request.url.encodedPath)
            assertEquals("""{"session":["owned-session"]}""", (request.body as TextContent).text)
            respond("")
        }).use {
            val client = ItbRestClient(it, apiUrl, "secret")
            assertFailsWith<IllegalArgumentException> { client.stop(emptyList()) }
            client.stop(listOf("owned-session"))
        }
    }

    @Test
    fun `HTTP errors preserve status without leaking response bodies or retrying start`() = runBlocking<Unit> {
        var calls = 0
        HttpClient(MockEngine { calls++; respond("sensitive-server-response", HttpStatusCode.TooManyRequests) }).use {
            val error = assertFailsWith<ItbRestClient.HttpFailure> {
                ItbRestClient(it, apiUrl, "secret").start("system", "actor", "suite", listOf("case"))
            }
            assertEquals(429, error.statusCode)
            assertFalse(error.toString().contains("sensitive-server-response"))
            assertNull(error.cause)
            assertEquals(1, calls)
        }
    }

    @Test
    fun `malformed server replies do not expose their payload and cancellation propagates`() = runBlocking<Unit> {
        HttpClient(MockEngine { respond("sensitive-invalid-json") }).use {
            val error = assertFailsWith<IllegalStateException> {
                ItbRestClient(it, apiUrl, "secret").status(listOf("run-1"))
            }
            assertFalse(error.toString().contains("sensitive-invalid-json"))
            assertNull(error.cause)
        }
        HttpClient(MockEngine { throw CancellationException("cancelled") }).use {
            assertFailsWith<CancellationException> { ItbRestClient(it, apiUrl, "secret").status(listOf("run-1")) }
        }
    }

    @Test
    fun `API credentials cannot be sent to a remote plaintext endpoint`() {
        HttpClient(MockEngine { error("No network request expected") }).use {
            assertFailsWith<IllegalArgumentException> { ItbRestClient(it, Url("http://testbed.example/api/rest"), "secret") }
            ItbRestClient(it, Url("http://localhost:9000/api/rest"), "secret")
        }
    }
}

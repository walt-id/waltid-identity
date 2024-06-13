import E2ETestWebService.test
import E2ETestWebService.testBlock
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class E2ETest {

    @Test
    fun e2e() = runTest(timeout = 5.minutes) {
        testBlock {
            var client = testHttpClient()

            // the e2e http request tests here
            test("/wallet-api/auth/user-info - not logged in without token") {
                client.get("/wallet-api/auth/user-info").apply {
                    assert(status == HttpStatusCode.Unauthorized)
                }
            }

            test("/wallet-api/auth/login - wallet-api login") {
                client.post("/wallet-api/auth/login") {
                    setBody(EmailAccountRequest(email = "user@email.com", password = "password").encodeWithType("email"))
                }.expectSuccess().apply {
                    body<JsonObject>().let { result ->
                        assertNotNull(result["token"])
                        val token = result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()

                        client = testHttpClient(token = token)
                    }
                }
            }

            test("/wallet-api/auth/user-info - logged in after login") {
                client.get("/wallet-api/auth/user-info").expectSuccess()
            }

            test("/wallet-api/wallet/accounts/wallets - get wallets") {
                client.get("/wallet-api/wallet/accounts/wallets").expectSuccess()

            }
        }
    }

    fun testHttpClient(token: String? = null) = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
            host = "127.0.0.1"
            port = 22222

            if (token != null) bearerAuth(token)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    private fun String.expectLooksLikeJwt(): String =
        also { assert(startsWith("ey") && count { it == '.' } == 2) { "Does not look like JWT" } }

    private fun HttpResponse.expectSuccess(): HttpResponse = also {
        assert(status.isSuccess()) { "HTTP status is non-successful" }
    }

}

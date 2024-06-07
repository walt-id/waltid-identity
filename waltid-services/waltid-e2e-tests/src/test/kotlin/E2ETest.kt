import E2ETestWebService.test
import E2ETestWebService.testBlock
import id.walt.crypto.utils.JsonUtils.toJsonObject
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class E2ETest {

    @Test
    fun e2e() = runTest(timeout = 5.minutes) {
        testBlock {
            /*val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
                install(DefaultRequest) {
                    contentType(ContentType.Application.Json)
                }
                install(Logging) {
                    this.level = LogLevel.ALL
                }
            }*/

            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json()
                }
                install(DefaultRequest) {
                    contentType(ContentType.Application.Json)
                    host = "127.0.0.1"
                    port = 22222
                }
                install(Logging) {
                    this.level = LogLevel.ALL
                }
            }

            // the e2e http request tests here
            test("wallet-api login") {
                client.post("/wallet-api/auth/login") {
                    setBody(
                        mapOf(
                            "type" to "email",
                            "email" to "user@email.com",
                            "password" to "password"
                        ).toJsonObject()
                    )
                }.let { println(it) }
            }

            test("other test") {
            }
        }
    }
}

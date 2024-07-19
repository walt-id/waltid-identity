import id.walt.commons.web.plugins.httpJson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ComponentTestBase : ComponentTest {

    @BeforeAll
    override fun setup() {
        TODO("Not yet implemented")
    }

    @AfterAll
    override fun tearDown() {
        TODO("Not yet implemented")
    }

    protected fun testHttpClient(token: String? = null) = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(httpJson)
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
}
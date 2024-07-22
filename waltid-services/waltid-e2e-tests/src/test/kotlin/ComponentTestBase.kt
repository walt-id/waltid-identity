import extensions.IssuerServiceExtension
import extensions.Login
import extensions.ServicesExtension
import extensions.StatsExtension
import id.walt.commons.web.plugins.httpJson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.net.URLDecoder
import kotlin.test.Test


@ExtendWith(StatsExtension::class)
//@ExtendWith(ServicesExtension::class)
@ExtendWith(IssuerServiceExtension::class)
class ComponentTestBase() : ComponentTest {

    @Test
    fun testTest(){
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

    protected fun loadResource(relativePath: String): String =
        URLDecoder.decode(this.javaClass.getResource(relativePath)!!.path, "UTF-8").let { File(it).readText() }
}
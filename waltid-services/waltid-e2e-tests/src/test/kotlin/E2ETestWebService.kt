import id.walt.commons.web.plugins.configureSerialization
import id.walt.commons.web.plugins.configureStatusPages
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.io.File
import java.net.URLDecoder

object E2ETestWebService {

    data class TestWebService(
        val module: Application.() -> Unit,
    ) {
        private val webServiceModule: Application.() -> Unit = {
            configureStatusPages()
            configureSerialization()

            module()
        }

        fun run(): suspend () -> Unit = {
            embeddedServer(
                CIO,
                port = 22222,
                host = "127.0.0.1",
                module = webServiceModule
            ).start(wait = false)
        }
    }

    suspend fun testBlock(block: suspend () -> Unit) {}

    suspend fun test(name: String, function: suspend () -> Any?) {
        runCatching { function.invoke() }
    }

    fun loadResource(relativePath: String): String =
        URLDecoder.decode(this.javaClass.getResource(relativePath)!!.path, "UTF-8").let { File(it).readText() }
}

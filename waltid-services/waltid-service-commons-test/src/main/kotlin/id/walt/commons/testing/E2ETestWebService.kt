package id.walt.commons.testing

import id.walt.commons.web.WebService
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

data class E2ETestWebService(
    val module: Application.() -> Unit,
) {
    private val webServiceModule = WebService(module).webServiceModule

    fun runService(block: suspend () -> Unit, host: String, port: Int): suspend () -> Unit = {
        embeddedServer(
            CIO,
            host = host,
            port = port,
            module = webServiceModule
        ).start(wait = false)

        block.invoke()
    }
}

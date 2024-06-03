package id.walt.web

import id.walt.config.ConfigManager
import id.walt.config.list.WebConfig
import id.walt.web.modules.ServiceHealthChecksDebugModule
import io.klogging.logger
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

data class WebService(
    val module: Application.() -> Unit,
) {
    private val log = logger("WebService")

    private val webServiceModule: Application.() -> Unit = {
        ServiceHealthChecksDebugModule.run { enable() }

        module.invoke(this)
    }

    suspend fun run(): suspend () -> Unit = suspend {
        val webConfig = ConfigManager.getConfig<WebConfig>()
        log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort})..." }
        embeddedServer(
            CIO,
            port = webConfig.webPort,
            host = webConfig.webHost,
            module = webServiceModule
        ).start(wait = true)
    }

}

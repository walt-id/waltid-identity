package id.walt.web

import id.walt.utils.RandomUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.engine.*

object Administration {
    private val logger = KotlinLogging.logger {}

    private val shutdownToken = RandomUtils.randomBase64UrlString(256)

    fun Application.configureAdministration() {
        logger.debug { "Shutdown token is: /application/shutdown/$shutdownToken" }
        install(ShutDownUrl.ApplicationCallPlugin) {
            shutDownUrl = "/application/shutdown/$shutdownToken"
            exitCodeSupplier = { 0 }
        }
    }
}

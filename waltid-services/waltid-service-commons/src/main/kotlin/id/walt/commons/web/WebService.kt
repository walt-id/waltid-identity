package id.walt.commons.web

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.WebConfig
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.commons.web.modules.FeatureFlagInformationModule
import id.walt.commons.web.modules.OpenApiModule
import id.walt.commons.web.modules.ServiceHealthChecksDebugModule
import id.walt.commons.web.plugins.configureSerialization
import id.walt.commons.web.plugins.configureStatusPages
import io.klogging.logger
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.net.BindException

data class WebService(
    val module: Application.() -> Unit,
) {
    private val log = logger("WebService")

    val webServiceModule: Application.() -> Unit = {
        { FeatureFlagInformationModule.run { enable() } } whenFeature CommonsFeatureCatalog.featureFlagInformationEndpointFeature
        { ServiceHealthChecksDebugModule.run { enable() } } whenFeature CommonsFeatureCatalog.healthChecksFeature
        { OpenApiModule.run { enable() } } whenFeature CommonsFeatureCatalog.openApiFeature
        { AuthenticationServiceModule.run { enable() } } whenFeature CommonsFeatureCatalog.authenticationServiceFeature

        configureStatusPages()
        configureSerialization()

        module.invoke(this)
    }

    suspend fun run(): suspend () -> Unit = suspend {
        val webConfig = ConfigManager.getConfig<WebConfig>()
        val host = webConfig.webHost
        val port = webConfig.webPort

        log.info { "Starting web server (binding to $host, listening on port $port)..." }

        val server = embeddedServer(
            CIO,
            host = host,
            port = port,
            module = webServiceModule
        )

        runCatching { server.start(wait = true) }.getOrElse { ex ->

            when {
                ex is BindException || ex.cause is BindException -> {
                    val bindEx = ex as? BindException ?: ex.cause as BindException

                    log.fatal(ex) {
                        """
                        |
                        |-------
                        |Failed to start web server for service: "${bindEx.localizedMessage}"
                        |Could not bind web server to port $port on host "$host".
                        |
                        |Please check if permissions to bind are correct and the port is not already being used by another program.
                        |-------
                        |
                        """.trimMargin()
                    }
                }

                else -> throw ex
            }


        }
    }

}

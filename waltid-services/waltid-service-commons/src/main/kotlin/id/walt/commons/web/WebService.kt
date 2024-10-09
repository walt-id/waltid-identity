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

data class WebService(
    val module: Application.() -> Unit,
) {
    private val log = logger("WebService")

    internal val webServiceModule: Application.() -> Unit = {
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
        log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort})..." }
        embeddedServer(
            CIO,
            port = webConfig.webPort,
            host = webConfig.webHost,
            module = webServiceModule
        ).start(wait = true)
    }

}

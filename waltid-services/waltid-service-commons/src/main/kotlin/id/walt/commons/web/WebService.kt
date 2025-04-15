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
import io.ktor.util.logging.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal val WORKING_DIRECTORY_PATH: String = SystemFileSystem.resolve(Path(".")).toString()

data class WebService(
    val module: Application.() -> Unit,
) {
    private val log = logger("WebService")

    val webServiceModule: Application.() -> Unit = {
        { FeatureFlagInformationModule.run { enable() } } whenFeature CommonsFeatureCatalog.featureFlagInformationEndpointFeature
        { ServiceHealthChecksDebugModule.run { enable() } } whenFeature CommonsFeatureCatalog.healthChecksFeature
        { OpenApiModule.run { enable(ConfigManager.getConfig<WebConfig>().rootPath) } } whenFeature CommonsFeatureCatalog.openApiFeature
        { AuthenticationServiceModule.run { enable() } } whenFeature CommonsFeatureCatalog.authenticationServiceFeature

        configureStatusPages()
        configureSerialization()

        module.invoke(this)
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun run(): suspend () -> Unit = suspend {
        val webConfig = ConfigManager.getConfig<WebConfig>()
        log.info { "Starting web server (binding to ${webConfig.webHost}, listening on port ${webConfig.webPort}, rootPath = '${webConfig.rootPath}') ..." }

        GlobalScope.embeddedServer(
            factory = CIO,
            port = webConfig.webPort,
            host = webConfig.webHost,
            rootPath = webConfig.rootPath,
            module = webServiceModule
        ).start(wait = true)
    }
}

fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> CoroutineScope.embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    port: Int = 80,
    host: String = "0.0.0.0",
    rootPath: String,
    watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH),
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    module: Application.() -> Unit,
): EmbeddedServer<TEngine, TConfiguration> {
    val connectors: Array<EngineConnectorConfig> = arrayOf(
        EngineConnectorBuilder().apply {
            this.port = port
            this.host = host
        }
    )
    val environment = applicationEnvironment {
        this.log = KtorSimpleLogger("io.ktor.server.Application")
    }
    val applicationProperties = serverConfig(environment) {
        this.parentCoroutineContext = coroutineContext + parentCoroutineContext
        this.watchPaths = watchPaths
        this.rootPath = rootPath
        this.module(module)
    }
    val config: TConfiguration.() -> Unit = {
        this.connectors.addAll(connectors)
    }
    return embeddedServer(factory, applicationProperties, config)
}
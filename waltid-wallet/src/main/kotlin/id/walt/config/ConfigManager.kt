package id.walt.config

import com.sksamuel.hoplite.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

interface WalletConfig

object ConfigManager {

    private val log = KotlinLogging.logger { }

    val registeredConfigurations = ConcurrentLinkedQueue<ConfigData>()
    val loadedConfigurations = HashMap<String, WalletConfig>()

    @OptIn(ExperimentalHoplite::class)
    private fun loadConfig(config: ConfigData, args: Array<String>) {
        val id = config.id
        log.debug { "Loading configuration: \"$id\"..." }

        val type = config.type

        runCatching {
            ConfigLoaderBuilder.default().addCommandLineSource(args)
                .addDefaultParsers()
                .addFileSource("config/$id.conf", optional = true)
                .addEnvironmentSource()
                .withExplicitSealedTypes()
                .build().loadConfigOrThrow(type, emptyList())
        }.onSuccess {
            loadedConfigurations[id] = it
            config.onLoad?.invoke(it)
        }.onFailure {
            log.error { "Could not load configuration for \"$id\": ${it.stackTraceToString()}" }
        }
    }

    inline fun <reified ConfigClass : WalletConfig> getConfigIdentifier(): String =
        registeredConfigurations.firstOrNull { it.type == ConfigClass::class }?.id
            ?: throw IllegalArgumentException("No such configuration registered: \"${ConfigClass::class.jvmName}\"!")

    inline fun <reified ConfigClass : WalletConfig> getConfig(): ConfigClass =
        getConfigIdentifier<ConfigClass>().let { configKey ->
            (loadedConfigurations[configKey]
                ?: throw NotFoundException("No loaded configuration: \"$configKey\"")).let { loadedConfig ->
                loadedConfig as? ConfigClass
                    ?: throw IllegalArgumentException("Invalid config class type: \"${loadedConfig::class.jvmName}\" is not a \"${ConfigClass::class.jvmName}\"!")
            }
        }


    private fun registerConfig(data: ConfigData) {
        if (registeredConfigurations.any { it.id == data.id }) throw IllegalArgumentException("A configuration with the name \"${data.id}\" already exists!")

        registeredConfigurations.add(data)
    }

    /**
     * All configurations registered in this function will be loaded on startup
     */
    private fun registerConfigurations() {
        registerConfig(ConfigData("db", DatabaseConfiguration::class) {
            registerConfig(ConfigData((it as DatabaseConfiguration).database, DatasourceConfiguration::class))
        })
        registerConfig(ConfigData("web", WebConfig::class))
        registerConfig(ConfigData("push", PushConfig::class))

        registerConfig(ConfigData("wallet", RemoteWalletConfig::class))
        registerConfig(ConfigData("marketplace", MarketPlaceConfiguration::class))
        registerConfig(ConfigData("chainexplorer", ChainExplorerConfiguration::class))
    }

    fun loadConfigs(args: Array<String>) {
        log.debug { "Loading configurations..." }

        if (registeredConfigurations.isEmpty()) registerConfigurations()

        registeredConfigurations.forEach {
            loadConfig(it, args)
        }
    }

    data class ConfigData(
        val id: String,
        val type: KClass<out WalletConfig>,
        val onLoad: ((WalletConfig) -> Unit)? = null,
    )
}

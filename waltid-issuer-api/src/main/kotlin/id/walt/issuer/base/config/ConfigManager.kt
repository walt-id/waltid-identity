package id.walt.issuer.base.config

import com.sksamuel.hoplite.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.*
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

interface BaseConfig

object ConfigManager {

    private val log = KotlinLogging.logger { }

    val registeredConfigurations = ArrayList<Pair<String, KClass<out BaseConfig>>>()
    val loadedConfigurations = HashMap<String, BaseConfig>()
    private val preloadedConfigurations = HashMap<String, BaseConfig>()

    fun preloadConfig(id: String, config: BaseConfig) {
        preloadedConfigurations[id] = config
    }

    @OptIn(ExperimentalHoplite::class)
    private fun loadConfig(config: Pair<String, KClass<out BaseConfig>>, args: Array<String>) {
        val id = config.first
        log.debug { "Loading configuration: \"$id\"..." }

        val type = config.second

        preloadedConfigurations[id]?.let {
            loadedConfigurations[id] = it
            log.info { "Overwrote issuer configuration with preload: $id" }
            return
        }

        runCatching {
            ConfigLoaderBuilder.default()
                .addCommandLineSource(args)
                .addFileSource("config/$id.conf", optional = true)
                .addEnvironmentSource()
                .withExplicitSealedTypes()
                .build().loadConfigOrThrow(type, emptyList())
        }.onSuccess {
            loadedConfigurations[id] = it
        }.onFailure {
            log.error { "Could not load configuration for \"$id\": ${it.stackTraceToString()}" }
        }
    }

    inline fun <reified ConfigClass : BaseConfig> getConfigIdentifier(): String =
        registeredConfigurations.firstOrNull { it.second == ConfigClass::class }?.first
            ?: throw IllegalArgumentException("No such configuration registered: \"${ConfigClass::class.jvmName}\"!")

    inline fun <reified ConfigClass : BaseConfig> getConfig(): ConfigClass =
        getConfigIdentifier<ConfigClass>().let { configKey ->
            (loadedConfigurations[configKey]
                ?: throw NotFoundException("No loaded configuration: \"$configKey\"")).let { loadedConfig ->
                loadedConfig as? ConfigClass
                    ?: throw IllegalArgumentException("Invalid config class type: \"${loadedConfig::class.jvmName}\" is not a \"${ConfigClass::class.jvmName}\"!")
            }
        }


    private fun registerConfig(name: String, config: KClass<out BaseConfig>) {
        if (registeredConfigurations.any { it.first == name }) throw IllegalArgumentException("A configuration with the name \"$name\" already exists!")

        registeredConfigurations.add(Pair(name, config))
    }

    /**
     * All configurations registered in this function will be loaded on startup
     */
    private fun registerConfigurations() {
        registerConfig("web", WebConfig::class)
        registerConfig("issuer-service", OIDCIssuerServiceConfig::class)
        registerConfig("credential-type", CredentialTypeConfig::class)
    }

    fun loadConfigs(args: Array<String>) {
        log.debug { "Loading configurations..." }

        if (registeredConfigurations.isEmpty()) registerConfigurations()

        registeredConfigurations.forEach {
            loadConfig(it, args)
        }
    }
}

package id.walt.commons.config

import com.sksamuel.hoplite.*
import io.klogging.noCoLogger
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

typealias WaltConfig = Any

class ConfigurationException(override val cause: ConfigException): IllegalArgumentException() {
    fun errorMessage() = cause.localizedMessage.replace(" com.typesafe.config.ConfigException\$Parse: Reader:", "")
}

object ConfigManager {

    private val log = noCoLogger("ConfigManager")

    val registeredConfigurations = ConcurrentLinkedQueue<ConfigData>()
    val loadedConfigurations = HashMap<Pair<String, KClass<out WaltConfig>>, WaltConfig>()
    val preloadedConfigurations = HashMap<Pair<String, KClass<out WaltConfig>>, WaltConfig>()

    val configLoaders = HashMap<String, ConfigLoader>()

    fun preclear() {
        registeredConfigurations.clear()
        loadedConfigurations.clear()
        preloadedConfigurations.clear()
        configLoaders.clear()
    }

    fun preloadAndRegisterConfig(id: String, config: WaltConfig) {
        registerConfig(id, config::class)
        preloadConfig(id, config)
    }

    fun preloadConfig(id: String, config: WaltConfig) {
        log.debug { "Preloading config \"$id\": $config" }
        preloadedConfigurations[Pair(id, config::class)] = config
    }

    @OptIn(ExperimentalHoplite::class)
    fun loadConfig(config: ConfigData, args: Array<String>): Result<WaltConfig> {
        val id = config.id
        val fullName = "\"$id\" (${config.type.simpleName ?: config.type.jvmName})"
        log.debug { "Loading configuration: $fullName..." }

        val type = config.type
        val configKey = Pair(id, type)

        preloadedConfigurations[configKey]?.let {
            loadedConfigurations[configKey] = it
            log.info { "Overwrote wallet configuration with preload: $fullName" }
            return Result.success(it)
        }

        return runCatching {
            ConfigLoaderBuilder.empty()
                .addDefaultDecoders()
                .addDefaultPreprocessors()
                //.addDefaultNodeTransformers()
                .addDefaultParamMappers()
                .addDefaultPropertySources()
                .addDefaultParsers()

                .addDecoder(JsonElementDecoder())
                .addCommandLineSource(args)
                .addDefaultParsers()
                .addEnvironmentSource(allowUppercaseNames = false)
                .addFileSource("config/$id.conf", optional = true)
                .withExplicitSealedTypes()
                .build().also { loader -> configLoaders[id] = loader }
                .loadConfigOrThrow(type, emptyList())
        }.onSuccess {
            log.trace { "Loaded config \"$id\": $it" }
            loadedConfigurations[configKey] = it
        }.onFailure {
            log.error {
                """
                    |---- vvv Configuration error for "$id" vvv ----
                    |Could not load configuration for $fullName: ${it.stackTraceToString()}
                    |---- ^^^ Configuration error for "$id" ^^^ ---
                    |""".trimMargin()
            }
        }
    }

    inline fun <reified ConfigClass : WaltConfig> getConfigIdentifier(): String =
        registeredConfigurations.firstOrNull { it.type == ConfigClass::class }?.id
            ?: throw IllegalArgumentException(
                "No such configuration registered: \"${ConfigClass::class.jvmName}\"!"
            )

    inline fun <reified ConfigClass : WaltConfig> getConfigLoader(): ConfigLoader =
        getConfigIdentifier<ConfigClass>().let { configKey ->
            configLoaders[configKey] ?: error("No config loader registered for: $configKey")
        }

    inline fun <reified ConfigClass : WaltConfig> getConfig(): ConfigClass =
        getConfigIdentifier<ConfigClass>().let { configKey ->
            (loadedConfigurations[Pair(configKey, ConfigClass::class)]
                ?: throw IllegalArgumentException("No loaded configuration: \"$configKey\""))
                .let { loadedConfig ->
                    loadedConfig as? ConfigClass
                        ?: throw IllegalArgumentException(
                            "Invalid config class type: \"${loadedConfig::class.jvmName}\" is not a \"${ConfigClass::class.jvmName}\"!"
                        )
                }
        }

    fun registerConfig(
        id: String,
        type: KClass<out WaltConfig>,
    ) = registerConfig(ConfigData(id, type))

    private fun registerConfig(data: ConfigData) {
        require(!registeredConfigurations.any { it.id == data.id }) { "A configuration with the name \"${data.id}\" already exists!" }

        registeredConfigurations.add(data)
    }

    fun testWithConfigs(vararg configs: Pair<String, KClass<out Any>>) = testWithConfigs(configs.toList())

    fun testWithConfigs(configs: List<Pair<String, KClass<out Any>>>) {
        log.info { "ConfigManager with test configurations:" }
        registeredConfigurations.clear()

        configs.forEach {
            registerConfig(it.first, it.second)
        }

        loadConfigs()
    }

    fun loadConfigs(args: Array<String> = emptyArray()) {
        log.debug { "Loading configurations..." }

        registeredConfigurations.forEach { loadConfig(it, args) }
    }

    data class ConfigData(
        val id: String,
        val type: KClass<out WaltConfig>,
    )
}

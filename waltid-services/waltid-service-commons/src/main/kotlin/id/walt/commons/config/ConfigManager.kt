package id.walt.commons.config

import com.sksamuel.hoplite.*
import io.klogging.java.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

typealias WaltConfig = Any

object ConfigManager {

    val log = LoggerFactory.getLogger(ConfigManager::class.java)!!

    val registeredConfigurations = ConcurrentLinkedQueue<ConfigData>()
    val loadedConfigurations = HashMap<Pair<String, KClass<out WaltConfig>>, WaltConfig>()
    val preloadedConfigurations = HashMap<Pair<String, KClass<out WaltConfig>>, WaltConfig>()

    val configLoaders = HashMap<String, ConfigLoader>()

    fun preloadAndRegisterConfig(id: String, config: WaltConfig) {
        registerConfig(id, config::class)
        preloadConfig(id, config)
    }

    fun preloadConfig(id: String, config: WaltConfig) {
        preloadedConfigurations[Pair(id, config::class)] = config
    }

    @OptIn(ExperimentalHoplite::class)
    fun loadConfig(config: ConfigData, args: Array<String>): Result<WaltConfig> {
        val id = config.id
        log.debug { "Loading configuration: \"$id\" (${config.type.simpleName ?: config.type.jvmName})..." }

        val type = config.type
        val configKey = Pair(id, type)

        preloadedConfigurations[configKey]?.let {
            loadedConfigurations[configKey] = it
            log.info { "Overwrote wallet configuration with preload: $id" }
            return Result.success(it)
        }

        return runCatching {
            ConfigLoaderBuilder.default()
                .addDecoder(JsonElementDecoder())
                .addCommandLineSource(args)
                .addDefaultParsers()
                .addEnvironmentSource()
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
                    |---- vvv Configuration error vvv ----
                    |Could not load configuration for "$id": ${it.stackTraceToString()}
                    |---- ^^^ Configuration error ^^^ ---
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
    ) = registerConfig(ConfigData(id, type/*, false, multiple, onLoad*/))

    fun registerRequiredConfig(
        id: String,
        type: KClass<out WaltConfig>,
    ) = registerConfig(ConfigData(id, type/*, true, multiple, onLoad*/))

    private fun registerConfig(data: ConfigData) {
        if (registeredConfigurations.any { it.id == data.id } /*&& !data.multiple*/)
            throw IllegalArgumentException(
                "A configuration with the name \"${data.id}\" already exists!"
            )

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

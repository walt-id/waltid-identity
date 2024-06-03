package id.walt.config

import com.sksamuel.hoplite.*
import io.klogging.java.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

//interface WaltConfig
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
    private fun loadConfig(config: ConfigData, args: Array<String>) {
        val id = config.id
        log.debug { "Loading ${if (config.required) "required" else "optional"} configuration: \"$id\" (${config.type.simpleName ?: config.type.jvmName})..." }

        val type = config.type
        val configKey = Pair(id, type)

        preloadedConfigurations[configKey]?.let {
            loadedConfigurations[configKey] = it
            log.info { "Overwrote wallet configuration with preload: $id" }
            return
        }

        runCatching {
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
            config.onLoad?.invoke(it)
        }.onFailure {
            if (config.required) {
                log.error {
                    """
                    |---- vvv Configuration error vvv ----
                    |Could not load configuration for "$id": ${it.stackTraceToString()}
                    |---- ^^^ Configuration error ^^^ ---
                    |""".trimMargin()
                }
            } else {
                if (it.message != null) {
                    val shorterMessage = it.message!!.removePrefix("Error loading config because:")
                        .lines().filterNot { it.isBlank() }.joinToString() { it.trim().removePrefix("- ") }
                    log.info { "OPTIONAL configuration \"$id\" not loaded: $shorterMessage [This is not a mistake if you are not using named feature. The feature will not be available when not configured.]" }
                } else {
                    log.info { "OPTIONAL configuration \"$id\" not loaded: ${it.stackTraceToString()}" }
                }
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
        multiple: Boolean = false,
        onLoad: ((WaltConfig) -> Unit)? = null,
    ) = registerConfig(ConfigData(id, type, false, multiple, onLoad))

    fun registerRequiredConfig(
        id: String,
        type: KClass<out WaltConfig>,
        multiple: Boolean = false,
        onLoad: ((WaltConfig) -> Unit)? = null,
    ) = registerConfig(ConfigData(id, type, true, multiple, onLoad))

    private fun registerConfig(data: ConfigData) {
        if (registeredConfigurations.any { it.id == data.id } && !data.multiple)
            throw IllegalArgumentException(
                "A configuration with the name \"${data.id}\" already exists!"
            )

        registeredConfigurations.add(data)
    }

    /** All configurations registered in this function will be loaded on startup */
    private fun registerConfigurations() {
        /*registerRequiredConfig("db", DatabaseConfiguration::class) {
            val dbConfigFile = (it as DatabaseConfiguration).database

            registerRequiredConfig(dbConfigFile, multiple = true, type = DatasourceJsonConfiguration::class)
        }
        registerRequiredConfig("web", WebConfig::class)
        registerRequiredConfig("logins", LoginMethodsConfig::class)
        registerRequiredConfig("auth", AuthConfig::class)

        registerConfig("tenant", TenantConfig::class)
        registerConfig("push", PushConfig::class)

        registerConfig("wallet", RemoteWalletConfig::class)
        registerConfig("marketplace", MarketPlaceConfiguration::class)
        registerConfig("chainexplorer", ChainExplorerConfiguration::class)
        registerConfig("runtime", RuntimeConfig::class)

        registerConfig("oidc", OidcConfiguration::class)
        registerConfig("trust", TrustConfig::class)
        registerConfig("rejectionreason", RejectionReasonConfig::class)
        registerConfig("registration-defaults", RegistrationDefaultsConfig::class)

        registerConfig("oci-rest-api", OciRestApiKeyConfig::class)
        registerConfig("notification", NotificationConfig::class)
        registerConfig("oci", OciKeyConfig::class)*/
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

        if (registeredConfigurations.isEmpty()) registerConfigurations()

        registeredConfigurations.forEach { loadConfig(it, args) }
    }

    data class ConfigData(
        val id: String,
        val type: KClass<out WaltConfig>,
        /** is this configuration mandatory or optional? */
        val required: Boolean = false,
        /** are multiple configurations of the same name allowed? */
        val multiple: Boolean = false,
        val onLoad: ((WaltConfig) -> Unit)? = null,
    )
}

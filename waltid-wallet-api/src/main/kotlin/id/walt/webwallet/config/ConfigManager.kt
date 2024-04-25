package id.walt.webwallet.config

import com.sksamuel.hoplite.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

interface WalletConfig

object ConfigManager {

    private val log = KotlinLogging.logger {}

    val registeredConfigurations = ConcurrentLinkedQueue<ConfigData>()
    val loadedConfigurations = HashMap<Pair<String, KClass<out WalletConfig>>, WalletConfig>()
    val preloadedConfigurations = HashMap<Pair<String, KClass<out WalletConfig>>, WalletConfig>()

    val configLoaders = HashMap<String, ConfigLoader>()

    fun preloadConfig(id: String, config: WalletConfig) {
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

    inline fun <reified ConfigClass : WalletConfig> getConfigIdentifier(): String =
        registeredConfigurations.firstOrNull { it.type == ConfigClass::class }?.id
            ?: throw IllegalArgumentException(
                "No such configuration registered: \"${ConfigClass::class.jvmName}\"!"
            )

    inline fun <reified ConfigClass : WalletConfig> getConfigLoader(): ConfigLoader =
        getConfigIdentifier<ConfigClass>().let { configKey ->
            configLoaders[configKey] ?: error("No config loader registered for: $configKey")
        }

    inline fun <reified ConfigClass : WalletConfig> getConfig(): ConfigClass =
        getConfigIdentifier<ConfigClass>().let { configKey ->
            (loadedConfigurations[Pair(configKey, ConfigClass::class)]
                ?: throw NotFoundException("No loaded configuration: \"$configKey\""))
                .let { loadedConfig ->
                    loadedConfig as? ConfigClass
                        ?: throw IllegalArgumentException(
                            "Invalid config class type: \"${loadedConfig::class.jvmName}\" is not a \"${ConfigClass::class.jvmName}\"!"
                        )
                }
        }

    fun registerConfig(
        id: String,
        type: KClass<out WalletConfig>,
        multiple: Boolean = false,
        onLoad: ((WalletConfig) -> Unit)? = null
    ) = registerConfig(ConfigData(id, type, false, multiple, onLoad))

    fun registerRequiredConfig(
        id: String,
        type: KClass<out WalletConfig>,
        multiple: Boolean = false,
        onLoad: ((WalletConfig) -> Unit)? = null
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
        registerRequiredConfig("db", DatabaseConfiguration::class) {
            val dbConfigFile = (it as DatabaseConfiguration).database

            registerRequiredConfig(dbConfigFile, multiple = true, type = DatasourceJsonConfiguration::class)
            registerRequiredConfig(dbConfigFile, multiple = true, type = DatasourceConfiguration::class)
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
        registerConfig("oci", OciKeyConfig::class)
    }

    fun loadConfigs(args: Array<String>) {
        log.debug { "Loading configurations..." }

        if (registeredConfigurations.isEmpty()) registerConfigurations()

        registeredConfigurations.forEach { loadConfig(it, args) }
    }

    data class ConfigData(
        val id: String,
        val type: KClass<out WalletConfig>,
        /** is this configuration mandatory or optional? */
        val required: Boolean = false,
        /** are multiple configurations of the same name allowed? */
        val multiple: Boolean = false,
        val onLoad: ((WalletConfig) -> Unit)? = null,
    )
}

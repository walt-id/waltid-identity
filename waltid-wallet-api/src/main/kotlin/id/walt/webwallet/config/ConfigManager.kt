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
    val loadedConfigurations = HashMap<String, WalletConfig>()
    val preloadedConfigurations = HashMap<String, WalletConfig>()

    val configLoaders = HashMap<String, ConfigLoader>()

    fun preloadConfig(id: String, config: WalletConfig) {
        preloadedConfigurations[id] = config
    }

    @OptIn(ExperimentalHoplite::class)
    private fun loadConfig(config: ConfigData, args: Array<String>) {
        val id = config.id
        log.debug { "Loading configuration: \"$id\"..." }

        val type = config.type

        preloadedConfigurations[id]?.let {
            loadedConfigurations[id] = it
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
            loadedConfigurations[id] = it
            config.onLoad?.invoke(it)
        }.onFailure {
            if (config.required) {
                log.error {
                    """
                    |---- vvv Configuration error vvv ----
                    |Could not load configuration for "$id": ${it.stackTraceToString()}
                    |---- ^^^ Configuration error ^^^ ---
                    |""".trimMargin() }
            } else {
                log.info { "Optional configuration not loaded: ${it.message}" }
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
            (loadedConfigurations[configKey]
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
        onLoad: ((WalletConfig) -> Unit)? = null
    ) = registerConfig(ConfigData(id, type, false, onLoad))

    fun registerRequiredConfig(
        id: String,
        type: KClass<out WalletConfig>,
        onLoad: ((WalletConfig) -> Unit)? = null
    ) = registerConfig(ConfigData(id, type, true, onLoad))

    private fun registerConfig(data: ConfigData) {
        if (registeredConfigurations.any { it.id == data.id })
            throw IllegalArgumentException(
                "A configuration with the name \"${data.id}\" already exists!"
            )

        registeredConfigurations.add(data)
    }

    /** All configurations registered in this function will be loaded on startup */
    private fun registerConfigurations() {
        registerRequiredConfig("db", DatabaseConfiguration::class) {
            registerRequiredConfig((it as DatabaseConfiguration).database, DatasourceConfiguration::class)
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

        registerConfig("oci", OciKeyConfig::class)
        registerConfig("notification", NotificationConfig::class)
    }

    fun loadConfigs(args: Array<String>) {
        log.debug { "Loading configurations..." }

        if (registeredConfigurations.isEmpty()) registerConfigurations()

        registeredConfigurations.forEach { loadConfig(it, args) }
    }

    data class ConfigData(
        val id: String,
        val type: KClass<out WalletConfig>,
        val required: Boolean = false,
        val onLoad: ((WalletConfig) -> Unit)? = null,
    )
}

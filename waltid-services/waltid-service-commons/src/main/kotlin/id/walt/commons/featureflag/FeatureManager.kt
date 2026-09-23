package id.walt.commons.featureflag

import com.sksamuel.hoplite.ConfigException
import id.walt.commons.config.ConfigManager
import id.walt.commons.config.ConfigurationException
import id.walt.commons.config.statics.RunConfiguration
import io.klogging.logger
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

object FeatureManager {
    // val baseFeatures = HashSet<String>()
    val enabledFeatures = HashSet<String>()
    val disabledFeatures = HashSet<String>()
    val registeredFeatures = HashMap<String, AbstractFeature>()
    val deprecatedFeatures = HashSet<String>()

    private val failed = ArrayList<Pair<AbstractFeature, Throwable>>()

    val featureAmendments = HashMap<AbstractFeature, suspend () -> Unit>()

    fun preclear() {
        enabledFeatures.clear()
        disabledFeatures.clear()
        registeredFeatures.clear()
        deprecatedFeatures.clear()
        failed.clear()
        featureAmendments.clear()
    }

    private val log = logger("FeatureManager")

    suspend fun enableFeatureAndIfNotSucceededRun(
        feature: AbstractFeature,
        ifNotSucceeded: (AbstractFeature, Throwable) -> Unit = { _, _ -> }
    ) {
        enableFeature(feature).ifResultNotSucceeded(feature) { ex -> ifNotSucceeded.invoke(feature, ex) }
    }

    suspend fun enableFeature(feature: AbstractFeature): Result<Boolean> {
        feature.dependsOn // todo: handle this

        if (disabledFeatures.contains(feature.name)) {
            log.info { "Will not enable \"${feature.name}\" as it was explicitly disabled." }
            return Result.success(false)
        }

        feature.configs.forEach { (name, config) ->
            log.info { "↳ Loading config \"$name\" for feature \"${feature.name}\"..." }
            ConfigManager.registerConfig(name, config)
            ConfigManager.loadConfig(ConfigManager.ConfigData(name, config), RunConfiguration.configArgs).onFailure { ex ->
                return Result.failure(
                    when (ex) {
                        is ConfigException -> ConfigurationException(ex)
                        else -> ex
                    }
                )
            }
        }

        featureAmendments[feature]?.let {
            log.info { "Amending feature \"${feature.name}\"..." }
            it.invoke()
        }

        if (!enabledFeatures.add(feature.name)) {
            return Result.failure(IllegalStateException("Feature \"${feature.name}\" already enabled."))
        }

        try {
            feature.onEnable?.let {
                log.info { "Enabling feature \"${feature.name}\"..." }
                it.invoke()
            }
        } catch (ex: Throwable) {
            enabledFeatures.remove(feature.name)
            return Result.failure(ex)
        }

        return Result.success(true)
    }

    fun disableFeature(feature: AbstractFeature) {
        disabledFeatures.add(feature.name)
    }

    fun isFeatureEnabled(featureName: String): Boolean {
        return enabledFeatures.contains(featureName) || (!disabledFeatures.contains(featureName) && when (val registeredFeature =
            registeredFeatures[featureName]) {
            is BaseFeature -> true
            is OptionalFeature -> registeredFeature.default.value
            else -> false
        })
    }

    fun isFeatureEnabled(feature: OptionalFeature): Boolean = isFeatureEnabled(feature.name)

    /**
     * Run block if provided feature is enabled
     */
    fun <T> runIfEnabledBlocking(feature: OptionalFeature, block: () -> T?): T? = runBlocking {
        runIfEnabled(feature, block)
    }

    suspend fun <T> runIfEnabled(feature: OptionalFeature, block: suspend () -> T?): T? =
        feature.takeIf { isFeatureEnabled(it) }?.let { block.invoke() }

    infix fun OptionalFeature.feature(block: () -> Unit) {
        runIfEnabledBlocking(this, block)
    }

    /**
     * ```kotlin
     * { your code... } whenFeature (FeatureCatalog.xyz)
     * ```
     */
    infix fun <T> (() -> T?).whenFeature(feature: OptionalFeature) = runIfEnabledBlocking(feature, this)

    suspend infix fun <T> (suspend () -> T?).whenFeatureSuspend(feature: OptionalFeature) = runIfEnabled(feature, this)

    suspend fun registerBaseFeatures(baseFeatures: List<BaseFeature>) {
        baseFeatures.forEach {
            log.debug { "Registering base feature \"${it.name}\"..." }
            registerFeature(it)
        }
    }

    suspend fun registerOptionalFeatures(optionalFeatures: List<OptionalFeature>) {
        optionalFeatures.forEach {
            log.debug { "Registering base feature \"${it.name}\"..." }
            registerFeature(it)
        }
    }

    suspend fun registerCatalog(catalog: ServiceFeatureCatalog) {
        registerBaseFeatures(catalog.baseFeatures)
        registerOptionalFeatures(catalog.optionalFeatures)
        registerDeprecatedFeatures(catalog.deprecatedFeatures)
    }

    suspend fun registerCatalogs(catalogs: List<ServiceFeatureCatalog>) {
        catalogs.forEach { catalog ->
            registerBaseFeatures(catalog.baseFeatures)
        }
        catalogs.forEach { catalog ->
            registerOptionalFeatures(catalog.optionalFeatures)
        }
        catalogs.forEach { catalog ->
            registerDeprecatedFeatures(catalog.deprecatedFeatures)
        }
    }

    fun registerFeature(feature: AbstractFeature) {
        registeredFeatures[feature.name] = feature
    }

    fun registerDeprecatedFeatures(names: Collection<String>) {
        deprecatedFeatures.addAll(names)
    }

    fun configuredFeature(name: String, action: String): AbstractFeature? {
        registeredFeatures[name]?.let { return it }
        check(name in deprecatedFeatures) {
            "Could not $action feature \"$name\" as it's not loaded/registered by any catalog. Registered features are: ${registeredFeatures.keys}"
        }
        return null
    }

    fun getDefaultedFeatures() =
        registeredFeatures.keys.subtract(enabledFeatures).subtract(disabledFeatures).subtract(failed.map { it.first.name }.toSet())

    fun getDefaultedAbstractFeatures() = getDefaultedFeatures().map { registeredFeatures[it]!! }


    private suspend fun Result<Boolean>.ifResultNotSucceeded(feature: AbstractFeature, block: (Throwable) -> Unit) {
        if (isFailure) {
            val exception = exceptionOrNull()!!

            if (exception !is ConfigurationException) // already handled by ConfigManager
                log.error(exception, "Did not succeed enabling feature: ${feature.name}")

            block.invoke(exception)
        }
    }

    suspend fun load(amendments: Map<AbstractFeature, suspend () -> Unit>) {
        featureAmendments.putAll(amendments)

        ConfigManager.registerConfig("_features", FeatureConfig::class)

        ConfigManager.loadConfigs(RunConfiguration.args)

        val config = ConfigManager.getConfig<FeatureConfig>()

        config.disabledFeatures.forEach { name ->
            val feature = configuredFeature(name, "disable")
            if (feature == null) {
                log.warn { "Ignoring deprecated feature flag \"$name\" listed in disabledFeatures. This flag no longer has any effect and can be removed from the configuration." }
            } else {
                disableFeature(feature)
            }
        }
        log.info { "Disabled features (${disabledFeatures.size}): ${disabledFeatures.joinToString()}" }




        registeredFeatures.filterValues { it is BaseFeature }.forEach { (name, feature) ->
            log.info { "Enabling base feature \"${feature.name}\"..." }

            enableFeatureAndIfNotSucceededRun(feature) { _, ex -> failed += feature to ex }
        }

        config.enabledFeatures.forEach { name ->
            val feature = configuredFeature(name, "enable")
            if (feature == null) {
                log.warn { "Ignoring deprecated feature flag \"$name\" listed in enabledFeatures. This flag no longer has any effect and can be removed from the configuration." }
            } else {
                log.info { "Enabling feature \"${feature.name}\"..." }
                enableFeatureAndIfNotSucceededRun(feature) { _, ex -> failed += feature to ex }
            }
        }
        log.info { "Enabled features (${enabledFeatures.size}): ${enabledFeatures.joinToString()}" }

        log.info { "Defaulted features (${getDefaultedFeatures().size}): ${getDefaultedFeatures().joinToString()}" }
        getDefaultedAbstractFeatures().forEach { feature ->
            if (feature.shouldDefaultEnable() && !failed.any { it.first == feature }) {
                log.info { "Enabling default feature \"${feature.name}\"..." }
                enableFeatureAndIfNotSucceededRun(feature) { _, ex ->
                    failed += feature to ex
                }
            }
        }


        if (failed.isNotEmpty()) {
            log.error {
                """
                |${failed.size} features failed to enable (see above for specifics) - error summary:
                ${
                    failed.mapIndexed { index, fail ->
                        "|- Failure ${index + 1}/${failed.size}: Loading \"${fail.first.name}\" failed: ${
                            when (val err = fail.second) {
                                is ConfigurationException -> err.errorMessage().summary()
                                else -> err.message?.shortSummary()
                            }
                        }"
                    }.joinToString(separator = "\n")
                }
                """.trimMargin()
            }
            exitProcess(1)
        }
    }
}

private fun String.shortSummary(roughLength: Int = 200) =
    summary()
        .let { if (it.length > roughLength * 1.2) it.take(roughLength).trim() + "……" else it }

private fun String.summary() =
    replaceWhile("\n\n", "\n")
        .trim()

private fun String.replaceWhile(old: String, new: String): String {
    var s = this
    while (s.contains(old)) s = s.replace(old, new)
    return s
}

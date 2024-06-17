package id.walt.commons.featureflag

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.statics.RunConfiguration
import io.klogging.logger

object FeatureManager {
    // val baseFeatures = HashSet<String>()
    val enabledFeatures = HashSet<String>()
    val disabledFeatures = HashSet<String>()
    val registeredFeatures = HashMap<String, AbstractFeature>()

    val featureAmendments = HashMap<AbstractFeature, suspend () -> Unit>()

    private val log = logger("FeatureManager")

    suspend fun enableFeature(feature: AbstractFeature): Boolean {
        feature.dependsOn // todo: handle this

        if (disabledFeatures.contains(feature.name)) {
            log.info { "Will not enable \"${feature.name}\" as it was explicitly disabled." }
            return true
        }

        feature.configs.forEach { (name, config) ->
            log.info { "↳ Loading config \"$name\" for feature \"${feature.name}\"..." }
            ConfigManager.registerConfig(name, config)
            ConfigManager.loadConfig(ConfigManager.ConfigData(name, config), RunConfiguration.args).onFailure {
                return false
            }
        }

        featureAmendments[feature]?.let {
            log.info { "Amending feature \"${feature.name}\"..." }
            it.invoke()
        }

        return enabledFeatures.add(feature.name)
    }

    fun disableFeature(feature: AbstractFeature) {
        disabledFeatures.add(feature.name)
    }

    fun isFeatureEnabled(featureName: String): Boolean {
        return enabledFeatures.contains(featureName) ||
                (!disabledFeatures.contains(featureName) &&
                        when (val registeredFeature = registeredFeatures[featureName]) {
                            is BaseFeature -> true
                            is OptionalFeature -> registeredFeature.default
                            else -> false
                        })
    }

    fun isFeatureEnabled(feature: OptionalFeature): Boolean =
        isFeatureEnabled(feature.name)

    /**
     * Run block if provided feature is enabled
     */
    fun runIfEnabled(feature: OptionalFeature, block: () -> Any?) {
        if (isFeatureEnabled(feature)) block.invoke()
    }

    infix fun OptionalFeature.feature(block: () -> Unit) {
        runIfEnabled(this, block)
    }

    /**
     * ```kotlin
     * { your code... } whenFeature (FeatureCatalog.xyz)
     * ```
     */
    infix fun (() -> Any?).whenFeature(feature: OptionalFeature) {
        runIfEnabled(feature, this)
    }

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
    }

    suspend fun registerCatalogs(catalogs: List<ServiceFeatureCatalog>) {
        catalogs.forEach { catalog ->
            registerBaseFeatures(catalog.baseFeatures)
        }
        catalogs.forEach { catalog ->
            registerOptionalFeatures(catalog.optionalFeatures)
        }
    }

    fun registerFeature(feature: AbstractFeature) {
        registeredFeatures[feature.name] = feature
    }

    fun getDefaultedFeatures() = registeredFeatures.keys.subtract(enabledFeatures).subtract(disabledFeatures)
    fun getDefaultedAbstractFeatures() =
        getDefaultedFeatures().map { registeredFeatures[it]!! }


    private fun Boolean.ifNotSucceeded(block: () -> Unit) {
        if (!this) block.invoke()
    }

    suspend fun load(amendments: Map<AbstractFeature, suspend () -> Unit>) {
        featureAmendments.putAll(amendments)

        ConfigManager.registerConfig("_features", FeatureConfig::class)

        ConfigManager.loadConfigs(RunConfiguration.args)

        val config = ConfigManager.getConfig<FeatureConfig>()

        config.disabledFeatures.forEach { name ->
            registeredFeatures[name]?.let { disableFeature(it) }
                ?: error("Could not disable feature \"$name\" as it's not loaded/registered by any catalog. Registered features are: ${registeredFeatures.keys}")
        }
        log.info { "Disabled features (${disabledFeatures.size}): ${disabledFeatures.joinToString()}" }


        val failed = ArrayList<AbstractFeature>()

        registeredFeatures.filterValues { it is BaseFeature }.forEach { (name, feature) ->
            log.info { "Enabling base feature \"${feature.name}\"..." }

            enableFeature(feature).ifNotSucceeded { failed += feature }
        }

        config.enabledFeatures.forEach { name ->
            registeredFeatures[name]?.let { feature ->
                log.info { "Enabling feature \"${feature.name}\"..." }
                enableFeature(feature).ifNotSucceeded { failed += feature }
            }
                ?: error("Could not enable feature \"$name\" as it's not loaded/registered by any catalog. Registered features are: ${registeredFeatures.keys}")
        }
        log.info { "Enabled features (${enabledFeatures.size}): ${enabledFeatures.joinToString()}" }

        log.info { "Defaulted features (${getDefaultedFeatures().size}): ${getDefaultedFeatures().joinToString()}" }
        getDefaultedAbstractFeatures().forEach {
            if (it is BaseFeature || (it is OptionalFeature && it.default)) {
                log.info { "Enabling default feature \"${it.name}\"..." }
                enableFeature(it).ifNotSucceeded { failed += it }
            }
        }


        if (failed.isNotEmpty()) {
            error("Failed to enable ${failed.size} features: ${failed.joinToString { it.name }}")
        }
    }
}


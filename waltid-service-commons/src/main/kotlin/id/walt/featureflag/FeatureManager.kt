package id.walt.featureflag

import id.walt.config.ConfigManager

object FeatureManager {
//    val enabledFeatures = HashMap<String, OptionalFeature>()
//    val registeredFeatures = HashMap<String, OptionalFeature>()
    val enabledFeatures = HashMap<String, OptionalFeature?>()

    fun enableFeature(feature: OptionalFeature) {
        enabledFeatures[feature.name] = feature
    }
    fun enableFeature(feature: String) {
        enabledFeatures[feature] = null
    }

    fun isFeatureEnabled(featureName: String): Boolean =
        enabledFeatures.containsKey(featureName)

    fun isFeatureEnabled(feature: OptionalFeature): Boolean =
        isFeatureEnabled(feature.name)

    /**
     * Run block if provided feature is enabled
     */
    fun runIfEnabled(feature: OptionalFeature, block: () -> Unit) {
        if (isFeatureEnabled(feature)) block.invoke()
    }

    infix fun OptionalFeature.feature(block: () -> Unit) {
        runIfEnabled(this, block)
    }

    infix fun (() -> Unit).whenFeature(feature: OptionalFeature) {
        runIfEnabled(feature, this)
    }

    /*fun registerFeature(feature: OptionalFeature) {
        registeredFeatures[feature.name] = feature
    }*/

    fun load() {
        val config = ConfigManager.getConfig<FeatureConfig>()
        config.enabledFeatures.forEach {
            enableFeature(it)
        }
    }
}


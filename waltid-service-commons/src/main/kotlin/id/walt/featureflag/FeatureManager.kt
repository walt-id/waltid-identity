package id.walt.featureflag

object FeatureManager {
    val enabledFeatures = HashMap<String, OptionalFeature>()

    fun enableFeature(feature: OptionalFeature) {
        enabledFeatures[feature.name] = feature
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
}

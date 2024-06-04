package id.walt.issuer

import id.walt.config.ConfigManager
import id.walt.featureflag.FeatureConfig
import id.walt.featureflag.FeatureManager

fun main() {
    ConfigManager.registerConfig("features", FeatureConfig::class)
    ConfigManager.loadConfigs(emptyArray())

    println(FeatureManager.isFeatureEnabled(FeatureCatalog.entra))
    FeatureManager.load()
    println(FeatureManager.isFeatureEnabled(FeatureCatalog.entra))
}

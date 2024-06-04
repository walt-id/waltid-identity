package id.walt.featureflag

import id.walt.config.WaltConfig

data class FeatureConfig(
    val enabledFeatures: List<String>
): WaltConfig()

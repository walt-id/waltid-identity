package id.walt.trust.service

import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.trust.service.config.TrustRegistryServiceConfig

object FeatureCatalog : ServiceFeatureCatalog {

    private val trustRegistry = BaseFeature(
        "trust-registry",
        "Trust Registry Service",
        TrustRegistryServiceConfig::class
    )

    override val baseFeatures = listOf(trustRegistry)
    override val optionalFeatures: List<OptionalFeature> = emptyList()
}

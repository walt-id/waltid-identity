package id.walt.featureflag

import id.walt.config.list.WebConfig
import id.walt.web.modules.ServiceHealthChecksDebugModule

object CommonsFeatureCatalog : ServiceFeatureCatalog {

    val webFeature = BaseFeature("web", "Web service", WebConfig::class)
    val featureFlagInformationEndpointFeature = OptionalFeature(
        "feature-flag-information-endpoint",
        "Enables endpoints related to showing information about available features on this service instance",
        default = true
    )
    val healthChecksFeature = OptionalFeature("healthchecks", "Enables healthcheck endpoints", default = true)
    val debugEndpointsFeature = OptionalFeature("debug-endpoints", "Enables various debug endpoints", default = true, config = ServiceHealthChecksDebugModule.ServiceDebugModuleConfiguration::class)

    override val baseFeatures: List<BaseFeature> = listOf(webFeature)
    override val optionalFeatures: List<OptionalFeature> =
        listOf(featureFlagInformationEndpointFeature, healthChecksFeature, debugEndpointsFeature)
}

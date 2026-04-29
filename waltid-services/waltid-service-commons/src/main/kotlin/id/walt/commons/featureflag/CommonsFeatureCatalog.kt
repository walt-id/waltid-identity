package id.walt.commons.featureflag

import id.walt.commons.config.list.WebConfig
import id.walt.commons.persistence.PersistenceConfiguration
import id.walt.commons.web.modules.ServiceHealthChecksDebugModule
import io.klogging.noCoLogger

object CommonsFeatureCatalog : ServiceFeatureCatalog {

    private val log = noCoLogger<CommonsFeatureCatalog>()

    val webFeature = BaseFeature("web", "Web service", WebConfig::class)
    val persistenceFeature = OptionalFeature("persistence", "Storage", PersistenceConfiguration::class, false)

    val featureFlagInformationEndpointFeature = OptionalFeature(
        "feature-flag-information-endpoint",
        "Enables endpoints related to showing information about available features on this service instance",
        default = true
    )
    val healthChecksFeature = OptionalFeature("healthchecks", "Enables healthcheck endpoints", default = true)

    val debugEndpointsFeature = OptionalFeature(
        "debug-endpoints",
        "Enables various debug endpoints",
        default = lazy { log.isDebugEnabled() },
        config = ServiceHealthChecksDebugModule.ServiceDebugModuleConfiguration::class
    )
    val openApiFeature = OptionalFeature("openapi", "Enables openapi endpoints", default = true)

    val authenticationServiceFeature = OptionalFeature("authservice", "Enables user authentication", default = true)

    override val baseFeatures: List<BaseFeature> = listOf(webFeature)
    override val optionalFeatures: List<OptionalFeature> =
        listOf(
            persistenceFeature,
            featureFlagInformationEndpointFeature,
            healthChecksFeature,
            debugEndpointsFeature,
            openApiFeature,
            authenticationServiceFeature
        )
}

package id.walt.verifier

import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.commons.persistence.PersistenceConfiguration
import id.walt.verifier.config.OIDCVerifierServiceConfig
import id.walt.verifier.entra.EntraConfig

object FeatureCatalog : ServiceFeatureCatalog {

    val verifierService = BaseFeature("verifier-service", "Verifier Service Implementation", OIDCVerifierServiceConfig::class)

    val entra = OptionalFeature("entra", "Enable Microsoft Entra support", EntraConfig::class, false)

    val persistenceService = BaseFeature("persistence", "Storage", PersistenceConfiguration::class)

    override val baseFeatures = listOf(verifierService, persistenceService)
    override val optionalFeatures = listOf(entra)
}
